package de.davis.keygo.migration.legacy_data

import android.content.Context
import androidx.room.Room
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
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyAesGcmCipher
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyKeyProvider
import de.davis.keygo.migration.legacy_data.data.json.LegacyDetailParser
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacySecureElementProbe
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTimestamps
import de.davis.keygo.migration.legacy_data.data.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.data.repository.LegacyItemRepositoryImpl
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseFiles
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyStoreCleaner
import de.davis.keygo.migration.legacy_data.domain.usecase.MigrateLegacyDataUseCase
import de.davisalessandro.keygo.rust.ItemAad
import io.mockk.every
import io.mockk.mockk
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
 * `SanitizingLegacyDatabaseProvider`, which opens in compat mode on the framework helper; a JVM test
 * has no framework helper, so this class opens in driver mode on the bundled driver. That gap is
 * accepted for the module rather than closed here.
 */
class LegacyMigrationEndToEndTest {

    private val tempDir: File = Files.createTempDirectory("legacy-e2e").toFile()
    private val dbFile = File(tempDir, "secure_element_database")

    /**
     * Room turns a database name into a path with `Context.getDatabasePath`, and so does the probe.
     * A fully relaxed mock answers that with a mock of its own whose path is empty, which makes both
     * of them quietly work on some other file: Room reports an empty database and the probe reports
     * nothing at all. Stubbing it is what points the two at the file these tests seed.
     *
     * This is the only mock in the class. Nothing else here has an Android framework type in the way.
     */
    private val context: Context = mockk<Context>(relaxed = true).apply {
        every { getDatabasePath(any()) } returns dbFile
    }

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

    private var keyStoreCleared = false

    private val database: LegacyDatabase = Room.databaseBuilder(
        context,
        LegacyDatabase::class.java,
        dbFile.name,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val databaseFiles = object : LegacyDatabaseFiles {
        override fun exists(): Boolean = dbFile.exists()

        override fun delete(): Boolean {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                File(dbFile.absolutePath + suffix).delete()
            }
            return true
        }
    }

    /**
     * Hands out the one database this class builds, and closes it on request. Closing has to work:
     * the tests that assert the file is gone would otherwise be deleting it from under an open Room
     * handle, which is not what production does.
     *
     * Nothing repairs anything here. The sanitizer only runs inside
     * `SanitizingLegacyDatabaseProvider`, which this class deliberately does not go through, so the
     * honest count is zero.
     */
    private val databaseProvider = object : LegacyDatabaseProvider {

        override val repairedRows: Int = 0

        override fun get(): LegacyDatabase = database

        override fun close() = database.close()
    }

    /** One key for the row blobs and the nested password blobs, because v1 used one alias for both. */
    private val legacyKeyProvider = object : LegacyKeyProvider {
        override fun secretKey(): SecretKey = legacyKey
    }

    private val legacyCipher = LegacyAesGcmCipher(legacyKeyProvider)

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
        // The production probe rather than a fake. This is the answer that decides whether the
        // user's file gets deleted, so each test earns its verdict from the bytes it actually put on
        // disk instead of from a constant the test handed back to itself.
        secureElementProbe = AndroidLegacySecureElementProbe(
            context = context,
            driver = BundledSQLiteDriver(),
        ),
        keyProvider = legacyKeyProvider,
        cipher = legacyCipher,
        parser = LegacyDetailParser(),
        databaseFiles = databaseFiles,
    )

    private val useCase = MigrateLegacyDataUseCase(
        legacyItemRepository = legacyRepository,
        legacyKeyStoreCleaner = object : LegacyKeyStoreCleaner {
            override fun deleteLegacyKey() {
                keyStoreCleared = true
            }
        },
        converter = LegacyItemConverter(
            cipher = legacyCipher,
            registrableDomainResolver = object : RegistrableDomainResolver {
                override fun resolve(domain: String): String? =
                    domain.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
                        ?.let { "example.com" }
            },
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
            val tagId = runCatching { dao.insertTag(LegacyTagEntity(name = name)) }.getOrElse { -1L }
            if (tagId > 0) dao.insertCrossRef(LegacySecureElementTagCrossRef(id, tagId))
        }
    }

    private fun seedVault() {
        vaultRepository.seed(
            Vault(
                id = vaultId,
                name = "Default Vault",
                keyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
                icon = Vault.Icon.Default,
            ),
        )
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
        vaultContextRepository.setContextAndLastInteracted(vaultId)
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

        assertTrue(keyStoreCleared)
        assertFalse(File(dbFile.absolutePath).exists())
        assertFalse(File(dbFile.absolutePath + "-wal").exists())
        assertFalse(File(dbFile.absolutePath + "-shm").exists())
    }

    @Test
    fun `keeps the database and reports the row when a blob will not decrypt`() = runTest {
        seedVault()
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Broken", blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), type = 1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals("Broken", outcome.report.failures.single().title)
        assertEquals(LegacyFailureReason.Undecryptable, outcome.report.failures.single().reason)
        assertFalse(keyStoreCleared)
        assertTrue(dbFile.exists())
        assertEquals(1, legacyRepository.remainingCount().assertSuccess())
    }

    @Test
    fun `keeps the database and reports the row when the json is malformed`() = runTest {
        seedVault()
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Garbled", blob = encryptLikeV1("not json".encodeToByteArray()), type = 1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals(LegacyFailureReason.Unparseable, outcome.report.failures.single().reason)
        assertTrue(dbFile.exists())
    }

    @Test
    fun `a second run after a partial failure produces exactly one copy of everything`() = runTest {
        seedVault()
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        seed(title = "Fine", blob = passwordJson("ada", "https://example.com", "pw"), type = 1)
        seed(title = "Broken", blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), type = 1)

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
        vaultContextRepository.setContextAndLastInteracted(vaultId)
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
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        // A file that exists but has no SecureElement table, the shape left behind by the
        // ItemDatabase rename. It has to be a database that really opens and really has no such
        // table, because that is the only evidence that earns the verdict that deletes it. A file of
        // arbitrary bytes would be a corrupt file, which the probe answers null for and which must
        // be left exactly where it was found.
        database.close()
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE Leftover (id INTEGER PRIMARY KEY)")
        }

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase())
        assertTrue(loginRepository.observeLogins().first().isEmpty())
        assertFalse(dbFile.exists())
        assertFalse(keyStoreCleared)
    }

    /**
     * The probe's contract is three-valued on purpose: true, false, or null for a file it could
     * not even open. Null is not a no. `LegacyItemRepositoryImpl.state()` only reaches `NotLegacy`,
     * the verdict that deletes the file, on a provable false; a file it cannot inspect at all has
     * to fall to `Unreadable` and be left standing, because a corrupt page and a half-restored
     * backup fail to open exactly the way a foreign file does, and answering "no v1 data here" on
     * that guess would throw away a user's only copy of their data.
     *
     * Four arbitrary bytes are not a SQLite file, so the production probe hits its own catch and
     * answers null, which is the one verdict nothing else in this suite drives.
     */
    @Test
    fun `keeps a file it could not inspect at all`() = runTest {
        seedVault()
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        dbFile.writeBytes(byteArrayOf(0, 1, 2, 3))

        assertIs<LegacyMigrationOutcome.Failed>(useCase())
        assertTrue(dbFile.exists())
        assertFalse(keyStoreCleared)
    }

    @Test
    fun `does nothing at all when no legacy file exists`() = runTest {
        seedVault()
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        database.close()
        databaseFiles.delete()

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase())
        assertNull(loginRepository.observeLogins().first().firstOrNull())
        assertFalse(keyStoreCleared)
    }
}
