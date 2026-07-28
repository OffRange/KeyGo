package de.davis.keygo.migration.legacy_data.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyKeyProvider
import de.davis.keygo.migration.legacy_data.data.json.LegacyDetailParser
import de.davis.keygo.migration.legacy_data.data.local.dao.LegacyElementDao
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacySecureElementProbe
import de.davis.keygo.migration.legacy_data.data.local.datasource.LEGACY_DATABASE_NAME
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseSanitizer
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacySecureElementProbe
import de.davis.keygo.migration.legacy_data.data.local.datasource.SanitizingLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTimestamps
import de.davis.keygo.migration.legacy_data.data.local.pojo.LegacyElementWithTags
import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseFiles
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reversible stand-in for the Keystore cipher. A blob prefixed with [FAIL] decrypts to null, which
 * is how tests simulate a row whose key is gone without needing a Keystore.
 */
private class FakeLegacyCipher : LegacyCipher {

    override fun decrypt(blob: ByteArray): ByteArray? {
        val text = blob.decodeToString()
        return if (text.startsWith(FAIL)) null else text.encodeToByteArray()
    }

    companion object {
        const val FAIL = "!!UNDECRYPTABLE!!"
    }
}

/**
 * A plain JCE key stands in for the Keystore one. Nothing here decrypts anything, so the bytes are
 * irrelevant; what matters is whether the alias resolves and how often it is asked.
 */
private class FakeLegacyKeyProvider(
    private val key: SecretKey? = SecretKeySpec(ByteArray(32), "AES"),
) : LegacyKeyProvider {

    var probes: Int = 0
        private set

    override fun secretKey(): SecretKey? {
        probes++
        return key
    }
}

/**
 * Answers the schema question without a file. [answer] carries the probe's three states: the table
 * is there, the file opened and it provably is not, or nothing could be read at all.
 */
private class FakeLegacySecureElementProbe(
    private val answer: Boolean? = true,
) : LegacySecureElementProbe {

    override fun hasSecureElementTable(): Boolean? = answer
}

/** Fails every read the way a cancelled unlock scope does, and nothing else. */
private class CancellingLegacyElementDao : LegacyElementDao {

    override suspend fun count(): Int = throw CancellationException("the unlock scope went away")

    override suspend fun getAllWithTags(): List<LegacyElementWithTags> =
        throw CancellationException("the unlock scope went away")

    override suspend fun deleteByIds(ids: List<Long>): Unit =
        throw CancellationException("the unlock scope went away")

    override suspend fun insertElement(element: LegacySecureElementEntity): Long =
        error("this fake is only ever read from")

    override suspend fun insertTag(tag: LegacyTagEntity): Long =
        error("this fake is only ever read from")

    override suspend fun insertCrossRef(crossRef: LegacySecureElementTagCrossRef): Unit =
        error("this fake is only ever read from")
}

/** Hands out one already-open in-memory database, or nothing when the file is unreadable. */
private class FakeLegacyDatabaseProvider(
    private val database: LegacyDatabase?,
) : LegacyDatabaseProvider {

    var closed: Boolean = false
        private set

    override var repairedRows: Int = 0

    override fun get(): LegacyDatabase? = database

    override fun close() {
        closed = true
    }
}

/** Answers from the real filesystem, so an empty directory models a clean install faithfully. */
private class FileBackedLegacyDatabaseFiles(private val file: File) : LegacyDatabaseFiles {

    override fun exists(): Boolean = file.exists()

    override fun delete(): Boolean = file.delete()
}

/**
 * @param databaseClosed reports whether the provider's handle was already closed. Captured at the
 * moment [delete] runs rather than read afterwards, because the order of the two is the behaviour:
 * asserting after the fact holds just as well when the file is deleted first.
 */
private class FakeLegacyDatabaseFiles(
    private val databaseClosed: () -> Boolean = { false },
) : LegacyDatabaseFiles {

    var present: Boolean = true
    var deleted: Boolean = false
        private set

    /** Null until [delete] runs, then whatever the provider's handle was at that instant. */
    var closedWhenDeleted: Boolean? = null
        private set

    override fun exists(): Boolean = present

    override fun delete(): Boolean {
        closedWhenDeleted = databaseClosed()
        deleted = true
        present = false
        return true
    }
}

class LegacyItemRepositoryImplTest {

    private lateinit var db: LegacyDatabase
    private lateinit var keyProvider: FakeLegacyKeyProvider
    private lateinit var databaseProvider: FakeLegacyDatabaseProvider
    private lateinit var databaseFiles: FakeLegacyDatabaseFiles
    private lateinit var secureElementProbe: LegacySecureElementProbe
    private lateinit var repository: LegacyItemRepositoryImpl

    /**
     * A real path inside a directory that starts out empty, which is exactly what a clean v2
     * install looks like: no `secure_element_database`, and nothing allowed to bring one into
     * being. Tests that need a file to be there seed one at this path themselves.
     */
    private val tempDir: File =
        java.nio.file.Files.createTempDirectory("legacy-on-disk").toFile()
    private val legacyFile: File = File(tempDir, LEGACY_DATABASE_NAME)
    private val legacyContext: Context = mockk<Context>(relaxed = true).apply {
        every { getDatabasePath(any()) } returns legacyFile
    }

    /**
     * Wired over the real provider, the real probe and a real path rather than the in-memory
     * database, because these tests turn on what is actually on disk: whether a file appears that
     * should not exist, and what the file that is there turns out to be. Fakes could not show that.
     *
     * Room gets a real driver here for the same reason. Left on its framework helper it cannot
     * create a file under a JVM test at all, so the assertion that no file appears would hold
     * whether or not the guard existed, and a test that cannot fail proves nothing.
     */
    private fun fileBackedRepository() = LegacyItemRepositoryImpl(
        databaseProvider = SanitizingLegacyDatabaseProvider(
            context = legacyContext,
            sanitizer = LegacyDatabaseSanitizer(BundledSQLiteDriver()),
            driver = BundledSQLiteDriver(),
        ),
        secureElementProbe = AndroidLegacySecureElementProbe(
            context = legacyContext,
            driver = BundledSQLiteDriver(),
        ),
        keyProvider = keyProvider,
        cipher = FakeLegacyCipher(),
        parser = LegacyDetailParser(),
        databaseFiles = FileBackedLegacyDatabaseFiles(legacyFile),
    )

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(mockk(relaxed = true), LegacyDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        keyProvider = FakeLegacyKeyProvider()
        databaseProvider = FakeLegacyDatabaseProvider(db)
        databaseFiles = FakeLegacyDatabaseFiles(databaseClosed = { databaseProvider.closed })
        secureElementProbe = FakeLegacySecureElementProbe()
        repository = newRepository()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun newRepository() = LegacyItemRepositoryImpl(
        databaseProvider = databaseProvider,
        secureElementProbe = secureElementProbe,
        keyProvider = keyProvider,
        cipher = FakeLegacyCipher(),
        parser = LegacyDetailParser(),
        databaseFiles = databaseFiles,
    )

    private suspend fun insert(
        title: String,
        json: String,
        favorite: Boolean = false,
        createdAt: Long? = 1_700_000_000_000L,
        modifiedAt: Long? = null,
        type: Int = 1,
        tags: List<String> = emptyList(),
    ): Long {
        val id = db.legacyElementDao().insertElement(
            LegacySecureElementEntity(
                title = title,
                data = json.encodeToByteArray(),
                favorite = favorite,
                timestamps = LegacyTimestamps(createdAt = createdAt, modifiedAt = modifiedAt),
            ).apply { this.type = type },
        )
        tags.forEach { name ->
            val tagId = db.legacyElementDao().insertTag(LegacyTagEntity(name = name))
            db.legacyElementDao().insertCrossRef(LegacySecureElementTagCrossRef(id, tagId))
        }
        return id
    }

    @Test
    fun `reads a password row with all of its fields`() = runTest {
        insert(
            title = "Example",
            json = """{"type":1,"username":"ada","origin":"https://example.com","strength":"WEAK"}""",
            favorite = true,
            modifiedAt = 1_700_000_999_000L,
        )

        val result = repository.readAll().assertSuccess()

        assertTrue(result.failures.isEmpty())
        val item = result.items.single()
        assertEquals("Example", item.title)
        assertTrue(item.favorite)
        assertEquals(1_700_000_000_000L, item.createdAt)
        assertEquals(1_700_000_999_000L, item.modifiedAt)
        assertEquals("ada", assertIs<LegacyDetail.Password>(item.detail).username)
    }

    /**
     * Schema version 1 had no timestamp columns, so a row that came up from it carries a real null.
     * Substituting a value here would hide that from the fallback the converter applies later.
     */
    @Test
    fun `carries a missing created at through as null`() = runTest {
        insert(title = "Old", json = """{"type":1}""", createdAt = null)

        assertNull(repository.readAll().assertSuccess().items.single().createdAt)
    }

    @Test
    fun `keeps user tags and drops element type tags`() = runTest {
        insert(
            title = "Example",
            json = """{"type":1,"username":"ada"}""",
            tags = listOf("work", "elementType:password", "personal"),
        )

        assertEquals(
            setOf("work", "personal"),
            repository.readAll().assertSuccess().items.single().tags,
        )
    }

    @Test
    fun `records an undecryptable row as a failure and keeps reading`() = runTest {
        insert(title = "Broken", json = "${FakeLegacyCipher.FAIL}whatever")
        insert(title = "Fine", json = """{"type":1,"username":"ada"}""")

        val result = repository.readAll().assertSuccess()

        assertEquals(listOf("Fine"), result.items.map { it.title })
        assertEquals(LegacyFailureReason.Undecryptable, result.failures.single().reason)
        assertEquals("Broken", result.failures.single().title)
    }

    @Test
    fun `records an unparseable row as a failure`() = runTest {
        insert(title = "Garbled", json = "definitely not json")

        assertEquals(
            LegacyFailureReason.Unparseable,
            repository.readAll().assertSuccess().failures.single().reason,
        )
    }

    @Test
    fun `records an unknown type as a failure`() = runTest {
        insert(title = "Alien", json = """{"type":42}""", type = 42)

        assertEquals(
            LegacyFailureReason.Unparseable,
            repository.readAll().assertSuccess().failures.single().reason,
        )
    }

    @Test
    fun `prune removes only the given rows`() = runTest {
        val keep = insert(title = "Keep", json = """{"type":1}""")
        val drop = insert(title = "Drop", json = """{"type":1}""")

        repository.prune(listOf(drop)).assertSuccess()

        assertEquals(listOf("Keep"), repository.readAll().assertSuccess().items.map { it.title })
        assertEquals(1, repository.remainingCount().assertSuccess())
        assertTrue(keep > 0)
    }

    @Test
    fun `reads an empty database as no items and no failures`() = runTest {
        val result = repository.readAll().assertSuccess()

        assertTrue(result.items.isEmpty())
        assertTrue(result.failures.isEmpty())
        assertEquals(0, repository.remainingCount().assertSuccess())
    }

    /**
     * A gone alias is one outcome for the whole run, not one failure per row. Both rows here would
     * decrypt fine under a key that still existed, so reporting them as two `Undecryptable` rows
     * would claim the entries were damaged when only the key is missing.
     */
    @Test
    fun `reports a missing legacy key once instead of one failure per row`() = runTest {
        insert(title = "First", json = """{"type":1}""")
        insert(title = "Second", json = """{"type":1}""")
        keyProvider = FakeLegacyKeyProvider(key = null)

        assertEquals(LegacyReadFailure.KeyUnavailable, newRepository().readAll().assertFailure())
    }

    @Test
    fun `probes the legacy key once for the whole read`() = runTest {
        repeat(3) { insert(title = "Row $it", json = """{"type":1}""") }

        repository.readAll().assertSuccess()

        assertEquals(1, keyProvider.probes)
    }

    /**
     * The provider hands back nothing when the file is corrupt or is not a database at all. That
     * has to arrive as a failure the migration can report, never as an exception escaping into the
     * unlock flow.
     */
    @Test
    fun `reports an unreadable database instead of throwing`() = runTest {
        databaseProvider = FakeLegacyDatabaseProvider(database = null)

        assertEquals(
            LegacyReadFailure.DatabaseUnreadable,
            newRepository().readAll().assertFailure(),
        )
    }

    @Test
    fun `state is absent when there is no legacy file`() = runTest {
        databaseFiles.present = false

        assertEquals(LegacyDatabaseState.Absent, repository.state())
    }

    /**
     * `Absent` and not `NotLegacy`, because the two lead to opposite places: `NotLegacy` gets the
     * file deleted, and deleting a file that was never there is at best confusing. Nor `Unreadable`,
     * which means a file we must leave alone.
     */
    @Test
    fun `state is absent on a clean install`() = runTest {
        assertEquals(LegacyDatabaseState.Absent, fileBackedRepository().state())
    }

    /**
     * The regression this guards is a file that should never have existed. This module only reads a
     * database it inherited, and Room creates any file it is asked to open, so a read on an install
     * that never ran v1 would leave an empty `secure_element_database` behind for every later run to
     * find and treat as inherited data.
     *
     * The read goes through `readAll` rather than `state`, because `state` is the caller's gate and
     * a gate nobody is forced through is not a guarantee. Asserting the disk rather than the return
     * value is the other half: the return value looks the same either way.
     */
    @Test
    fun `reading on a clean install leaves no legacy file behind`() = runTest {
        val result = fileBackedRepository().readAll()

        assertFalse(
            legacyFile.exists(),
            "reading must never bring a legacy database into existence",
        )
        assertEquals(LegacyReadFailure.DatabaseUnreadable, result.assertFailure())
    }

    @Test
    fun `state is present when the file opens as a v1 database`() = runTest {
        insert(title = "Example", json = """{"type":1}""")

        assertEquals(LegacyDatabaseState.Present, repository.state())
    }

    /**
     * Unreadable rather than [LegacyDatabaseState.NotLegacy] on purpose: a file that cannot be
     * opened tells us nothing about what is inside it, and NotLegacy is the state that gets the
     * file deleted. The probe answers null here for the same reason it would in production, where
     * a file the provider cannot open is a file the probe cannot read either.
     */
    @Test
    fun `state is unreadable when the file cannot be opened`() = runTest {
        databaseProvider = FakeLegacyDatabaseProvider(database = null)
        secureElementProbe = FakeLegacySecureElementProbe(answer = null)

        assertEquals(LegacyDatabaseState.Unreadable, newRepository().state())
    }

    /**
     * The leftover v2 database from before `ItemDatabase` was renamed. No `SecureElement` table
     * means no v1 data, and this is the one shape of file that earns the verdict that deletes it.
     *
     * Run over a real file rather than a fake probe, because the claim is about what is inside the
     * file and a fake could only restate the expectation.
     */
    @Test
    fun `state is not legacy when the file has no SecureElement table`() = runTest {
        BundledSQLiteDriver().open(legacyFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE Leftover (id INTEGER PRIMARY KEY)")
        }

        assertEquals(LegacyDatabaseState.NotLegacy, fileBackedRepository().state())
    }

    /**
     * The regression that matters most in this class. This file has the `SecureElement` table, so
     * it is not a leftover v2 database, but Room refuses it on the first query. Folding that into
     * [LegacyDatabaseState.NotLegacy] deletes a file that was never shown to be free of v1 data,
     * and a corrupt page, a disk that fills up during the 2-to-3 recreate and a cancelled run all
     * arrive here by the same route.
     *
     * Seeded with v1's table name but not v1's schema, which is what Room's own integrity check
     * rejects. A real inherited file that fails deeper down produces the same shape of failure.
     */
    @Test
    fun `state is unreadable when the file has the table but the query fails`() = runTest {
        BundledSQLiteDriver().open(legacyFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE SecureElement (id INTEGER PRIMARY KEY)")
            connection.execSQL("PRAGMA user_version = 3")
        }

        assertEquals(LegacyDatabaseState.Unreadable, fileBackedRepository().state())
    }

    /**
     * Cancelling the unlock scope is not a statement about the user's file. `runCatching` here
     * would fold it into `DatabaseUnreadable` and let a run that never finished answer for what is
     * on disk, so this seam rethrows where the other repositories in the codebase do not.
     */
    @Test
    fun `lets a cancellation out instead of reporting the file as unreadable`() = runTest {
        // mockk stands in for the Room-generated database container alone, which has no fake to
        // build from; the DAO underneath it, which is what the behaviour is about, is a real fake.
        val database = mockk<LegacyDatabase> {
            every { legacyElementDao() } returns CancellingLegacyElementDao()
        }
        databaseProvider = FakeLegacyDatabaseProvider(database)

        assertFailsWith<CancellationException> { newRepository().remainingCount() }
    }

    @Test
    fun `deleteDatabase closes the open handle before removing the file`() = runTest {
        assertTrue(repository.deleteDatabase())

        assertTrue(databaseFiles.deleted)
        assertEquals(
            true,
            databaseFiles.closedWhenDeleted,
            "the file cannot be deleted from under an open handle",
        )
    }

    @Test
    fun `reports the rows the sanitizer repaired`() = runTest {
        databaseProvider.repairedRows = 4

        assertEquals(4, repository.repairedRows)
    }
}
