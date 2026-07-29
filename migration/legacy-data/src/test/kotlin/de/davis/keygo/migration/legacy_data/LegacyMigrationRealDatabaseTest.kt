package de.davis.keygo.migration.legacy_data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeItemTransactionRunner
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.migration.legacy_data.data.FakeLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.FakeLegacyKeyRepository
import de.davis.keygo.migration.legacy_data.data.FakeRegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyAesGcmCipher
import de.davis.keygo.migration.legacy_data.data.json.LegacyDetailParser
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.data.repository.LegacyItemRepositoryImpl
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.usecase.MigrateLegacyDataUseCase
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.YearMonth
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the same import as [LegacyMigrationEndToEndTest], but over a `secure_element_database` (with
 * its `-wal`/`-shm`) captured from an actual v1 build instead of rows this module wrote itself.
 *
 * Every other test in this module proves the migration handles what its authors believed v1 could
 * produce: a row shape, a JSON shape, a failure mode. That is exactly the blind spot a hand-rolled
 * fixture cannot see past, because the same belief that shaped the production code also shaped the
 * test data. This class cannot prove the decision logic, only whether the crypto, the parser and the
 * converter agree with something none of them produced.
 *
 * The Keystore-backed key never leaves hardware, so it cannot be part of any file that ends up on
 * disk. The three rows below were captured after that alias was swapped for the static AES-256 key
 * `keygo_v1_migrate_to_v2__data_key` (32 ASCII bytes), which is why that string is reconstructed
 * here rather than read from anywhere production would use.
 *
 * Edge cases (corrupt files, malformed JSON, partial-failure retries, stale v2 leftovers, batch
 * size) stay in [LegacyMigrationEndToEndTest], which can vary them freely; this class only ever
 * has the one fixture and is not the place to grow more scenarios.
 */
class LegacyMigrationRealDatabaseTest {

    private val tempDir: File = Files.createTempDirectory("legacy-real-db").toFile()
    private val dbFile = File(tempDir, "secure_element_database")

    init {
        copyFixture("secure_element_database", dbFile)
        copyFixture("secure_element_database-wal", File(tempDir, "secure_element_database-wal"))
        copyFixture("secure_element_database-shm", File(tempDir, "secure_element_database-shm"))
    }

    private val vaultId = newVaultId()

    private val loginRepository = FakeLoginRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val transactionRunner = FakeItemTransactionRunner()
    private val cryptoProvider = FakeCryptographicScopeProvider(FakeItemRepository(loginRepository))

    private val database: LegacyDatabase = Room.databaseBuilder<LegacyDatabase>(dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val databaseProvider = FakeLegacyDatabaseProvider(file = dbFile, database = database)

    private val legacyKeyRepository = FakeLegacyKeyRepository(STATIC_KEY)
    private val legacyCipher = LegacyAesGcmCipher(legacyKeyRepository)

    private val legacyRepository = LegacyItemRepositoryImpl(
        databaseProvider = databaseProvider,
        keyRepository = legacyKeyRepository,
        cipher = legacyCipher,
        parser = LegacyDetailParser(),
    )

    private val useCase = MigrateLegacyDataUseCase(
        legacyItemRepository = legacyRepository,
        legacyKeyRepository = legacyKeyRepository,
        converter = LegacyItemConverter(
            cipher = legacyCipher,
            registrableDomainResolver = FakeRegistrableDomainResolver(),
        ),
        cryptographicScopeProvider = cryptoProvider,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        upsertVaultItem = UpsertVaultItemUseCase(loginRepository, creditCardRepository),
        transactionRunner = transactionRunner,
    )

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    private fun copyFixture(name: String, destination: File) {
        val resource = requireNotNull(javaClass.getResourceAsStream("/legacy-fixtures/$name")) {
            "Missing test fixture /legacy-fixtures/$name on the test classpath"
        }
        resource.use { input -> destination.outputStream().use { input.copyTo(it) } }
    }

    private suspend fun seedVault() {
        vaultRepository.seed(
            Vault(
                id = vaultId,
                name = "Default Vault",
                keyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
                icon = Vault.Icon.Default,
            ),
        )
        vaultContextRepository.setContextAndLastInteracted(vaultId)
    }

    private suspend fun readBackPassword(loginId: ItemId): String =
        cryptoProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
                vaultId = vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(itemId = loginId, vaultId = vaultId),
            ),
        ) {
            loginRepository.getLoginById(loginId)!!.passwordCredential!!.secret.decrypt()
        }.assertSuccess()

    private data class DecryptedCard(
        val holder: String?,
        val cardNumber: String?,
        val cvv: String?,
        val expirationDate: YearMonth?,
    )

    private suspend fun readBackCard(cardId: ItemId): DecryptedCard =
        cryptoProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
                vaultId = vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(itemId = cardId, vaultId = vaultId),
            ),
        ) {
            val card = creditCardRepository.getCreditCardById(cardId)!!
            DecryptedCard(
                holder = card.holder,
                cardNumber = card.cardNumber?.decrypt(),
                cvv = card.cvv?.decrypt(),
                expirationDate = card.expirationDate,
            )
        }.assertSuccess()

    @Test
    fun `migrates a v1 database captured from a real build without losing any of its data`() =
        runTest {
            seedVault()

            val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

            assertEquals(3, outcome.report.migratedItems)
            assertFalse(outcome.report.hasFailures)

            val logins = loginRepository.observeLogins().first()
            assertEquals(2, logins.size)

            val withOrigin = logins.single { it.name == "Test Password Title" }
            assertEquals("user@something.com", withOrigin.username)
            assertEquals("web.website.com", withOrigin.domainInfos.single().value)
            assertEquals(setOf("Tag1", "Tag2"), withOrigin.tags.map { it.display }.toSet())
            assertEquals("s3cret123@", readBackPassword(withOrigin.id))

            // Seeded with no username or origin at all, which v1 stored as empty strings rather
            // than nulls; the converter is what turns blank strings back into "not present".
            val withoutOrigin = logins.single { it.name == "Random Password" }
            assertNull(withoutOrigin.username)
            assertTrue(withoutOrigin.domainInfos.isEmpty())
            assertTrue(withoutOrigin.tags.isEmpty())
            assertEquals("7'|MW_x5", readBackPassword(withoutOrigin.id))

            val migratedCardId = creditCardRepository.store.first().values.first().id
            val card = readBackCard(migratedCardId)
            assertEquals("Holder Some Card", card.holder)
            // v1 stored "4111 1111 1111 1111"; the converter strips separators to match what
            // v2's own entry form would have stored for the same card.
            assertEquals("4111111111111111", card.cardNumber)
            assertEquals("123", card.cvv)
            assertEquals(YearMonth.of(2026, 7), card.expirationDate)
            assertEquals(
                setOf("Tag1", "CardTag"),
                creditCardRepository.getCreditCardById(migratedCardId)!!.tags.map { it.display }
                    .toSet(),
            )

            assertTrue(legacyKeyRepository.deleted)
            assertFalse(dbFile.exists())
            assertFalse(File(dbFile.absolutePath + "-wal").exists())
            assertFalse(File(dbFile.absolutePath + "-shm").exists())
        }

    private companion object {
        val STATIC_KEY =
            SecretKeySpec("keygo_v1_migrate_to_v2__data_key".toByteArray(Charsets.UTF_8), "AES")
    }
}
