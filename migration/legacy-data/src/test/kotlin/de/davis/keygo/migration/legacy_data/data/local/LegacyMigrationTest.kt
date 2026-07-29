package de.davis.keygo.migration.legacy_data.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the hand-written 2-to-3 lands on exactly the schema v1 shipped, and that it deals with the
 * rows a generated recreate would have aborted on.
 *
 * Opening through real Room is the schema assertion, and it is a strong one. After running a
 * migration Room compares the resulting tables against the schema it expects for that version and
 * throws `Migration didn't properly handle ...` on any drift in a column name, affinity,
 * nullability, default, primary key, index or foreign key. So every test here that reaches a row
 * has already proved the DDL in
 * [de.davis.keygo.migration.legacy_data.data.local.migration.LegacyMigration2To3] is a faithful copy
 * of v1's. Opening through [AndroidLegacyDatabaseProvider] rather than a bare builder means the
 * tests also fail if production ever forgets to register the migration.
 */
class LegacyMigrationTest {

    private val tempDir: File = Files.createTempDirectory("legacy-migration").toFile()
    private val dbFile: File = File(tempDir, "secure_element_database")

    private fun openMigrated(): LegacyDatabase =
        assertNotNull(
            AndroidLegacyDatabaseProvider(legacyContext(dbFile), BundledSQLiteDriver()).get(),
        )

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun SQLiteConnection.insertV2(
        title: String?,
        data: ByteArray?,
        type: Int = 1,
        favorite: Int = 0,
    ) {
        prepare(
            "INSERT INTO SecureElement (title, data, type, favorite) VALUES (?, ?, ?, ?)",
        ).use { stmt ->
            if (title == null) stmt.bindNull(1) else stmt.bindText(1, title)
            if (data == null) stmt.bindNull(2) else stmt.bindBlob(2, data)
            stmt.bindLong(3, type.toLong())
            stmt.bindLong(4, favorite.toLong())
            stmt.step()
        }
    }

    private fun tablesOnDisk(): List<String> =
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
            ).use { stmt ->
                buildList {
                    while (stmt.step()) add(stmt.getText(0))
                }
            }
        }

    @Test
    fun `migrates a version 2 file onto v1's version 3 schema`() = runTest {
        seedLegacyDatabase(dbFile, version = 2).use { connection ->
            connection.insertV2("Kept", byteArrayOf(1, 2, 3), type = 17, favorite = 1)
            connection.execSQL(
                "UPDATE SecureElement SET created_at = 1700000000000, modified_at = 1700000009999",
            )
        }

        // Reaching a row at all is the schema assertion: Room validates after migrating.
        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(1, rows.size)
        val element = rows.single().element
        assertEquals("Kept", element.title)
        assertEquals(17, element.type)
        assertTrue(element.favorite)
        assertEquals(1_700_000_000_000L, element.timestamps.createdAt)
        assertEquals(1_700_000_009_999L, element.timestamps.modifiedAt)
        assertEquals(3, userVersionOf(dbFile))
    }

    @Test
    fun `migrates a version 1 file, running the generated 1-to-2 first`() = runTest {
        seedLegacyDatabase(dbFile, version = 1).use { connection ->
            connection.prepare(
                "INSERT INTO SecureElement (title, data, type) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.bindText(1, "Old entry")
                stmt.bindBlob(2, byteArrayOf(9))
                stmt.bindLong(3, 1)
                stmt.step()
            }
        }

        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(listOf("Old entry"), rows.map { it.element.title })
        assertEquals(3, userVersionOf(dbFile))
    }

    /**
     * The row is deleted rather than repaired, because there is no ciphertext in it to recover. v1
     * wrote it when `Cryptography.encryptAES` swallowed a Keystore failure and returned null, and
     * v1 could not read it back either. Coalescing it to an empty blob would leave a row that can
     * never decrypt, and one of those keeps the import reporting a failure forever, which would
     * leave the legacy file and v1's Keystore alias on disk for good.
     */
    @Test
    fun `drops a row whose blob is null and keeps every other row`() = runTest {
        seedLegacyDatabase(dbFile, version = 2).use { connection ->
            connection.insertV2("Has a blob", byteArrayOf(1))
            connection.insertV2("No blob at all", null)
            connection.insertV2("Also has a blob", byteArrayOf(2))
        }

        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(listOf("Has a blob", "Also has a blob"), rows.map { it.element.title })
    }

    /**
     * The opposite call to the one above, and the reason the two columns cannot share a rule. The
     * blob beside a null title is intact, so this row is a recoverable credential that happens to
     * have no display name. Deleting it would throw away a password to save a string.
     */
    @Test
    fun `keeps a row whose title is null and empties the title instead`() = runTest {
        seedLegacyDatabase(dbFile, version = 2).use { connection ->
            connection.insertV2(null, byteArrayOf(4, 5, 6))
        }

        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(1, rows.size)
        assertEquals("", rows.single().element.title)
        assertContentEquals(byteArrayOf(4, 5, 6), rows.single().element.data)
    }

    @Test
    fun `drops the MasterPassword table and creates the tag tables`() = runTest {
        seedLegacyDatabase(dbFile, version = 2).use { connection ->
            connection.insertV2("Anything", byteArrayOf(1))
        }
        assertTrue("MasterPassword" in tablesOnDisk(), "seed is wrong")

        val db = openMigrated()
        db.legacyElementDao().count()
        db.close()

        val tables = tablesOnDisk()
        assertTrue("MasterPassword" !in tables, "v2's MasterPassword must not survive")
        assertTrue("Tag" in tables)
        assertTrue("SecureElementTagCrossRef" in tables)
    }
}
