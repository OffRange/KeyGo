package de.davis.keygo.migration.legacy_data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeItemTransactionRunner
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.migration.legacy_data.data.FakeLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.FakeLegacyKeyRepository
import de.davis.keygo.migration.legacy_data.data.FakeRegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyAesGcmCipher
import de.davis.keygo.migration.legacy_data.data.json.LegacyDetailParser
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTimestamps
import de.davis.keygo.migration.legacy_data.data.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.data.repository.LegacyItemRepositoryImpl
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.usecase.MigrateLegacyDataUseCase
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.time.YearMonth
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Drives the whole import over a real database file: real Room entities, v1's real AES-GCM, the
 * real JSON parser, the real converter and the real use case.
 *
 * Every earlier test in this module proved one link. Two properties only exist between links and so
 * can only be proved here. The first is v1's double encryption: a password was encrypted once on its
 * own and then again inside the row blob, so the plaintext only comes back if the repository, the
 * parser and the converter each undo their own layer in the right order. The second is the order of
 * prune and delete: the file is only destroyed once the rows it held are provably in v2.
 *
 * The one thing left standing in for production is the v2 side of the crypto.
 * [FakeCryptographicScopeProvider] re-encrypts with a reversible XOR rather than the Rust wrap and
 * unwrap, which need the native library that a JVM test does not have. The v1 side, which is what
 * this module owns, is the real cipher throughout.
 *
 * The other known ceiling is how Room is opened. Production goes through
 * `AndroidLegacyDatabaseProvider`, which opens in compat mode on the framework helper; a JVM test
 * has no framework helper, so this class opens in driver mode on the bundled driver. That gap is
 * accepted for the module rather than closed here.
 */
class LegacyMigrationEndToEndTest {

    private val tempDir: File = Files.createTempDirectory("legacy-e2e").toFile()
    private val dbFile = File(tempDir, "secure_element_database")

    private val legacyKey: SecretKey = KeyGenerator.getInstance("AES")
        .apply { init(256, SecureRandom()) }
        .generateKey()

    private val vaultId = newVaultId()

    private val loginRepository = FakeLoginRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val transactionRunner = FakeItemTransactionRunner()
    private val cryptoProvider = FakeCryptographicScopeProvider(FakeItemRepository(loginRepository))

    /**
     * File backed, not in memory, and that is the point of this class rather than an incidental
     * choice. The import's whole verdict is read off the bytes at [dbFile] through Room, and the
     * provider deletes that same path afterwards, so an in-memory database would leave the file
     * absent, the run answering `DatabaseEmpty` for the wrong reason and the prune-before-delete
     * ordering with nothing to order.
     *
     * Room opens lazily, so building this up front still leaves each test free to write the file
     * underneath it first. Built with the path-only overload rather than the one taking a Context,
     * so no Android framework type has to be stood in for at all.
     */
    private val database: LegacyDatabase = Room.databaseBuilder<LegacyDatabase>(dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val databaseProvider = FakeLegacyDatabaseProvider(file = dbFile, database = database)

    private val legacyKeyRepository = FakeLegacyKeyRepository(legacyKey)
    private val legacyCipher = LegacyAesGcmCipher(legacyKeyRepository)

    /** Id the migration mints for the seeded card, learned through [cardIdCapturingRepository]. */
    private var migratedCardId: ItemId? = null

    /**
     * `CreditCardRepository` only queries by id, and the id a migrated card gets is a fresh
     * `newItemId()` minted deep inside the use case, not something this test can predict up front.
     * Wrapping the write is how the test learns it, rather than adding a list query to production
     * for the sake of this one assertion.
     */
    private val cardIdCapturingRepository = object : CreditCardRepository by creditCardRepository {
        override suspend fun createOrUpdateCreditCard(card: CreditCard): Result<ItemId, Throwable> =
            creditCardRepository.createOrUpdateCreditCard(card).also { result ->
                if (result is Result.Success) migratedCardId = result.success
            }
    }

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
        upsertVaultItem = UpsertVaultItemUseCase(loginRepository, cardIdCapturingRepository),
        transactionRunner = transactionRunner,
    )

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    /** Byte-for-byte v1's `Cryptography.encryptAES`: 12 byte IV prefix, AES-256-GCM, 128 bit tag. */
    private fun encryptLikeV1(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    /** GSON wrote `byte[]` as a JSON array of signed ints. */
    private fun ByteArray.asJsonArray(): String = joinToString(",", "[", "]")

    private fun passwordJson(username: String, origin: String, password: String): ByteArray {
        val nested = encryptLikeV1(password.encodeToByteArray()).asJsonArray()
        return encryptLikeV1(
            """{"type":1,"username":"$username","origin":"$origin","password":$nested,"strength":"STRONG"}"""
                .encodeToByteArray(),
        )
    }

    /**
     * Long enough to clear the IV length check and nothing but garbage after it, so it reaches
     * AES-GCM and fails the tag there rather than being rejected on its size.
     */
    private fun undecryptableBlob(): ByteArray = ByteArray(13) { (it + 1).toByte() }

    private fun cardJson(): ByteArray = encryptLikeV1(
        """{"type":17,"cardholder":{"firstName":"Ada","lastName":"Lovelace"},
           "expirationDate":"04/29","cardNumber":"4111111111111111","cvv":"123"}"""
            .encodeToByteArray(),
    )

    private suspend fun seed(
        title: String,
        blob: ByteArray,
        type: Int,
        favorite: Boolean = false,
        createdAt: Long? = 1_700_000_000_000L,
        modifiedAt: Long? = null,
        tags: List<String> = emptyList(),
    ) {
        val dao = database.legacyElementDao()
        val id = dao.insertElement(
            LegacySecureElementEntity(
                title = title,
                data = blob,
                favorite = favorite,
                timestamps = LegacyTimestamps(createdAt, modifiedAt),
            ).apply { this.type = type },
        )
        tags.forEach { name ->
            // `Tag.name` is uniquely indexed, so a name another row already carries throws rather
            // than returning an id. Reusing it is not worth the query; the cross ref is what these
            // tests read back, and every one of them seeds distinct names.
            val tagId =
                runCatching { dao.insertTag(LegacyTagEntity(name = name)) }.getOrElse { -1L }
            if (tagId > 0) dao.insertCrossRef(LegacySecureElementTagCrossRef(id, tagId))
        }
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
            // No wrappedItemKeyInformation, so this mints a fresh key rather than unwrapping the
            // item's stored one. That is only safe because FakeCryptographicScopeProvider XORs
            // against one fixed key no matter what it is handed; a key-aware fake would break this
            // in a way that would be confusing to track down.
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(itemId = loginId, vaultId = vaultId),
            ),
        ) {
            loginRepository.getLoginById(loginId)!!.passwordCredential!!.secret.decrypt()
        }.assertSuccess()

    /** What a migrated card looks like once its secret fields are decrypted back to plaintext. */
    private data class DecryptedCard(
        val holder: String?,
        val cardNumber: String?,
        val cvv: String?,
        val expirationDate: YearMonth?,
    )

    /**
     * The card path has no nested encryption the way v1's password does: `cardNumber` and `cvv`
     * are encrypted once, directly, under the item key. Reading them back only has to undo that
     * one layer.
     */
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
    fun `migrates a full v1 database and leaves nothing behind`() = runTest {
        seedVault()
        seed(
            title = "Example",
            blob = passwordJson("ada", "https://example.com", "hunter2"),
            type = 1,
            favorite = true,
            modifiedAt = 1_700_000_999_000L,
            tags = listOf("work", "elementType:password"),
        )
        seed(title = "Card", blob = cardJson(), type = 17, tags = listOf("finance"))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(2, outcome.report.migratedItems)
        assertFalse(outcome.report.hasFailures)

        val login = loginRepository.observeLogins().first().single()
        assertEquals("Example", login.name)
        assertEquals("ada", login.username)
        assertTrue(login.pinned)
        assertEquals(setOf("work"), login.tags.map { it.display }.toSet())
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), login.timestamp.createdAt)
        assertEquals(
            Instant.fromEpochMilliseconds(1_700_000_999_000L),
            login.timestamp.modifiedAt,
        )
        assertEquals("hunter2", readBackPassword(login.id))

        val card = readBackCard(migratedCardId!!)
        assertEquals("Ada Lovelace", card.holder)
        assertEquals("4111111111111111", card.cardNumber)
        assertEquals("123", card.cvv)
        assertEquals(YearMonth.of(2029, 4), card.expirationDate)

        assertTrue(legacyKeyRepository.deleted)
        assertFalse(dbFile.exists())
        assertFalse(File(dbFile.absolutePath + "-wal").exists())
        assertFalse(File(dbFile.absolutePath + "-shm").exists())
    }

    @Test
    fun `keeps the database and reports the row when a blob will not decrypt`() = runTest {
        seedVault()
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Broken", blob = undecryptableBlob(), type = 1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals("Broken", outcome.report.failures.single().title)
        assertEquals(LegacyFailureReason.Unreadable, outcome.report.failures.single().reason)
        assertFalse(legacyKeyRepository.deleted)
        assertTrue(dbFile.exists())
        assertEquals(1, legacyRepository.remainingCount().assertSuccess())
    }

    @Test
    fun `keeps the database and reports the row when the json is malformed`() = runTest {
        seedVault()
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Garbled", blob = encryptLikeV1("not json".encodeToByteArray()), type = 1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals(LegacyFailureReason.Unreadable, outcome.report.failures.single().reason)
        assertTrue(dbFile.exists())
    }

    @Test
    fun `a second run after a partial failure produces exactly one copy of everything`() = runTest {
        seedVault()
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Broken", blob = undecryptableBlob(), type = 1)

        assertIs<LegacyMigrationOutcome.Migrated>(useCase())
        val afterFirst = loginRepository.observeLogins().first().map { it.name }

        val second = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(0, second.report.migratedItems)
        assertEquals(1, second.report.failures.size)
        assertEquals(afterFirst, loginRepository.observeLogins().first().map { it.name })
        assertEquals(listOf("Fine"), afterFirst)
    }

    @Test
    fun `migrates a five hundred item database completely`() = runTest {
        seedVault()
        repeat(500) { index ->
            seed(
                title = "Entry $index",
                blob = passwordJson("user$index", "https://example.com", "pw$index"),
                type = 1,
            )
        }

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(500, outcome.report.migratedItems)
        assertEquals(500, loginRepository.observeLogins().first().size)
        assertEquals(1, transactionRunner.transactionCount)
        assertFalse(dbFile.exists())
    }

    @Test
    fun `deletes a stale v2 development database without importing anything`() = runTest {
        seedVault()
        // A file that exists but has no SecureElement table, the shape left behind by the
        // ItemDatabase rename. It has to be a database that really opens and really has no such
        // table, because that is the only evidence that earns the verdict that deletes it. A file of
        // arbitrary bytes would be a corrupt file, which comes back Unreadable and which must be
        // left exactly where it was found. Nothing has the path open yet: Room waits for its first
        // query.
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE Leftover (id INTEGER PRIMARY KEY)")
        }

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase())
        assertTrue(loginRepository.observeLogins().first().isEmpty())
        assertFalse(dbFile.exists())
        // The alias goes with the file on every path that deletes it. Nothing it could open is left
        // on disk, and the run that finds no file at all can no longer reach it.
        assertTrue(legacyKeyRepository.deleted)
    }

    /**
     * `Unreadable` is not a zero, and keeping the two apart is what makes the deletion safe. Only a
     * count that was actually taken reaches `DatabaseEmpty`, the failure that deletes the file; a
     * file that cannot be opened at all has to fall to `DatabaseUnreadable` and be left standing,
     * because a corrupt page and a half-restored backup fail to open exactly the way a foreign file
     * does, and answering "no v1 data here" on that guess would throw away a user's only copy of
     * their data.
     *
     * Four arbitrary bytes are not a SQLite file, so Room throws on the first query and the read
     * answers Unreadable, which is the one verdict nothing else in this suite drives.
     */
    @Test
    fun `keeps a file it could not inspect at all`() = runTest {
        seedVault()
        dbFile.writeBytes(byteArrayOf(0, 1, 2, 3))

        assertIs<LegacyMigrationOutcome.Failed>(useCase())
        assertTrue(dbFile.exists())
        assertFalse(legacyKeyRepository.deleted)
    }

    @Test
    fun `does nothing at all when no legacy file exists`() = runTest {
        seedVault()
        databaseProvider.delete()

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase())
        assertNull(loginRepository.observeLogins().first().firstOrNull())
        // Nothing was deleted, because there was nothing to delete, so the alias has no reason to go
        // and no file appeared at `dbFile` for the next run to find.
        assertFalse(legacyKeyRepository.deleted)
        assertFalse(dbFile.exists())
    }
}
