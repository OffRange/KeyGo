package de.davis.keygo.legacy_migration.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.legacy_migration.data.FakeLegacyCipher
import de.davis.keygo.legacy_migration.data.FakeLegacyDatabase
import de.davis.keygo.legacy_migration.data.FakeLegacyDatabaseProvider
import de.davis.keygo.legacy_migration.data.FakeLegacyElementDao
import de.davis.keygo.legacy_migration.data.FakeLegacyKeyRepository
import de.davis.keygo.legacy_migration.data.json.LegacyDetailParser
import de.davis.keygo.legacy_migration.data.local.datasource.AndroidLegacyDatabaseProvider
import de.davis.keygo.legacy_migration.data.local.datasource.LEGACY_DATABASE_NAME
import de.davis.keygo.legacy_migration.data.local.datasource.LegacyDatabase
import de.davis.keygo.legacy_migration.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.legacy_migration.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.legacy_migration.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.legacy_migration.data.local.entity.LegacyTagEntity
import de.davis.keygo.legacy_migration.data.local.entity.LegacyTimestamps
import de.davis.keygo.legacy_migration.data.local.legacyContext
import de.davis.keygo.legacy_migration.domain.model.LegacyDetail
import de.davis.keygo.legacy_migration.domain.model.LegacyFailureReason
import de.davis.keygo.legacy_migration.domain.model.LegacyReadFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
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

class LegacyItemRepositoryImplTest {

    private lateinit var db: LegacyDatabase
    private lateinit var keyRepository: FakeLegacyKeyRepository
    private lateinit var databaseProvider: FakeLegacyDatabaseProvider
    private lateinit var repository: LegacyItemRepositoryImpl

    /**
     * A real path inside a directory that starts out empty, which is exactly what a clean v2
     * install looks like: no `secure_element_database`, and nothing allowed to bring one into
     * being. Tests that need a file to be there seed one at this path themselves.
     */
    private val tempDir: File = Files.createTempDirectory("legacy-on-disk").toFile()
    private val legacyFile: File = File(tempDir, LEGACY_DATABASE_NAME)

    /**
     * Wired over the real provider and a real path rather than the in-memory database, because
     * these tests turn on what is actually on disk: whether a file appears that should not exist,
     * and what the file that is there turns out to be. Fakes could not show that.
     *
     * Room gets a real driver here for the same reason. Left on its framework helper it cannot
     * create a file under a JVM test at all, so the assertion that no file appears would hold
     * whether or not the guard existed, and a test that cannot fail proves nothing.
     */
    private fun fileBackedRepository() = repositoryOver(
        AndroidLegacyDatabaseProvider(
            context = legacyContext(legacyFile),
            driver = BundledSQLiteDriver(),
        ),
    )

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<LegacyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        keyRepository = FakeLegacyKeyRepository()
        databaseProvider = FakeLegacyDatabaseProvider(legacyFile, db)
        repository = newRepository()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun newRepository() = repositoryOver(databaseProvider)

    private fun repositoryOver(provider: LegacyDatabaseProvider) = LegacyItemRepositoryImpl(
        databaseProvider = provider,
        keyRepository = keyRepository,
        cipher = FakeLegacyCipher(),
        parser = LegacyDetailParser(),
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
        assertEquals(LegacyFailureReason.Unreadable, result.failures.single().reason)
        assertEquals("Broken", result.failures.single().title)
    }

    @Test
    fun `records an unparseable row as a failure`() = runTest {
        insert(title = "Garbled", json = "definitely not json")

        assertEquals(
            LegacyFailureReason.Unreadable,
            repository.readAll().assertSuccess().failures.single().reason,
        )
    }

    @Test
    fun `records an unknown type as a failure`() = runTest {
        insert(title = "Alien", json = """{"type":42}""", type = 42)

        assertEquals(
            LegacyFailureReason.Unreadable,
            repository.readAll().assertSuccess().failures.single().reason,
        )
    }

    @Test
    fun `prune removes only the given rows`() = runTest {
        insert(title = "Keep", json = """{"type":1}""")
        val drop = insert(title = "Drop", json = """{"type":1}""")

        repository.prune(listOf(drop)).assertSuccess()

        assertEquals(listOf("Keep"), repository.readAll().assertSuccess().items.map { it.title })
        assertEquals(1, repository.remainingCount().assertSuccess())
    }

    /**
     * SQLite's bound-parameter limit was 999 until 3.32.0, and Android's system SQLite still holds
     * to it through API 30: a single `DELETE ... WHERE id IN (...)` over more ids than that throws
     * instead of deleting. This repository runs on [BundledSQLiteDriver] in every JVM test, whose
     * bundled SQLite has no such ceiling, so this test cannot reproduce the throw itself; what it
     * proves instead is the invariant that makes the throw impossible regardless of which SQLite a
     * device is actually running: no call to the DAO ever carries more than a safe number of ids.
     */
    @Test
    fun `prune splits large batches across several deletes`() = runTest {
        val dao = FakeLegacyElementDao((1L..1200L).toSet())
        val repo = repositoryOver(FakeLegacyDatabaseProvider(legacyFile, FakeLegacyDatabase(dao)))

        repo.prune((1L..1200L).toList()).assertSuccess()

        assertTrue(
            dao.deleteCalls.all { it.size <= 500 },
            "A v1 vault above SQLite's parameter limit must still prune in one call to prune(), " +
                    "just split across several deletes underneath. A single oversized call throws, the " +
                    "prune is reported as failed, and the whole vault reimports on the next unlock.",
        )
        assertEquals(1200, dao.deleteCalls.sumOf { it.size })
        assertTrue(dao.remainingIds.isEmpty())
    }

    /**
     * A file with nothing in it is a failure rather than an empty success, because the caller acts
     * on it: `DatabaseEmpty` is what earns the file its deletion. An empty success would be read as
     * "imported everything, which was nothing" and fall through the import instead.
     */
    @Test
    fun `reads an empty database as the failure that earns a delete`() = runTest {
        assertEquals(LegacyReadFailure.DatabaseEmpty, repository.readAll().assertFailure())
        assertEquals(0, repository.remainingCount().assertSuccess())
    }

    /**
     * A gone alias is one outcome for the whole run, not one failure per row. Both rows here would
     * decrypt fine under a key that still existed, so reporting them as two `Unreadable` rows
     * would claim the entries were damaged when only the key is missing.
     */
    @Test
    fun `reports a missing legacy key once instead of one failure per row`() = runTest {
        insert(title = "First", json = """{"type":1}""")
        insert(title = "Second", json = """{"type":1}""")
        keyRepository = FakeLegacyKeyRepository(key = null)

        assertEquals(LegacyReadFailure.KeyUnavailable, newRepository().readAll().assertFailure())
    }

    @Test
    fun `probes the legacy key once for the whole read`() = runTest {
        repeat(3) { insert(title = "Row $it", json = """{"type":1}""") }

        repository.readAll().assertSuccess()

        assertEquals(1, keyRepository.probes)
    }

    /**
     * The provider hands back nothing when there is no file at the legacy path, which is a clean
     * install and holds nothing to lose. That has to arrive as a failure the migration can report,
     * never as an exception escaping into the unlock flow.
     */
    @Test
    fun `reports no provider as an empty database instead of throwing`() = runTest {
        databaseProvider = FakeLegacyDatabaseProvider(legacyFile, database = null)

        assertEquals(LegacyReadFailure.DatabaseEmpty, newRepository().readAll().assertFailure())
    }

    /**
     * The regression this guards is a file that should never have existed. This module only reads a
     * database it inherited, and Room creates any file it is asked to open, so a read on an install
     * that never ran v1 would leave an empty `secure_element_database` behind for every later run to
     * find and treat as inherited data.
     *
     * Asserting the disk rather than the return value is the point: the return value would look the
     * same whether or not the guard held.
     */
    @Test
    fun `reading on a clean install leaves no legacy file behind`() = runTest {
        val result = fileBackedRepository().readAll()

        assertFalse(
            legacyFile.exists(),
            "reading must never bring a legacy database into existence",
        )
        assertEquals(LegacyReadFailure.DatabaseEmpty, result.assertFailure())
    }

    /**
     * The regression that matters most in this class. This file has the `SecureElement` table and a
     * row in it, but Room refuses it on the first query. It must not come back as
     * [LegacyReadFailure.DatabaseEmpty], which would delete a file holding a row nobody has ever
     * read: a corrupt page, a disk that fills up during the 2-to-3 recreate and a cancelled run all
     * arrive here by the same route.
     *
     * `DatabaseUnreadable` is the right shape. The count could not be taken at all, so nothing is
     * known about what is in the file; that is reported, and the caller leaves the file standing.
     *
     * Seeded with v1's table name but not v1's schema, which is what Room's own integrity check
     * rejects. A real inherited file that fails deeper down produces the same shape of failure.
     */
    @Test
    fun `a file Room refuses is unreadable, never empty`() = runTest {
        BundledSQLiteDriver().open(legacyFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE SecureElement (id INTEGER PRIMARY KEY)")
            connection.execSQL("INSERT INTO SecureElement (id) VALUES (1)")
            connection.execSQL("PRAGMA user_version = 3")
        }
        val repository = fileBackedRepository()

        assertEquals(LegacyReadFailure.DatabaseUnreadable, repository.readAll().assertFailure())
        assertTrue(legacyFile.exists(), "a file nobody could read must survive the run")
    }

    /**
     * Cancelling the unlock scope is not a statement about the user's file. `runCatching` here
     * would fold it into `DatabaseUnreadable` and let a run that never finished answer for what is
     * on disk, so this seam rethrows where the other repositories in the codebase do not.
     */
    @Test
    fun `lets a cancellation out instead of reporting the file as unreadable`() = runTest {
        val dao = FakeLegacyElementDao().apply {
            countFailure = CancellationException("the unlock scope went away")
        }
        databaseProvider = FakeLegacyDatabaseProvider(legacyFile, FakeLegacyDatabase(dao))

        assertFailsWith<CancellationException> { newRepository().remainingCount() }
    }

    @Test
    fun `deleteDatabase closes the open handle before removing the file`() = runTest {
        legacyFile.createNewFile()

        assertTrue(repository.deleteDatabase(), "the file was there and had to go")
        assertTrue(databaseProvider.closed, "the file cannot be deleted from under an open handle")
    }
}
