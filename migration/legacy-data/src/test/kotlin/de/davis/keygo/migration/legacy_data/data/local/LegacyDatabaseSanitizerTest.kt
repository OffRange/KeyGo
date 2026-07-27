package de.davis.keygo.migration.legacy_data.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseSanitizer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sanitizer exists because a NULL `title` or `data` in a version 1 or 2 file aborts Room's
 * 2-to-3 recreate and takes the whole database with it. `LegacyDatabaseOpenTest` proves the hazard;
 * these tests prove the repair clears it without disturbing anything else.
 */
class LegacyDatabaseSanitizerTest {

    private val tempDir: File = java.nio.file.Files.createTempDirectory("legacy-sanitize").toFile()
    private val dbFile: File = File(tempDir, "secure_element_database")

    private val json = Json { ignoreUnknownKeys = true }

    private val sanitizer = LegacyDatabaseSanitizer(BundledSQLiteDriver())

    private val context: Context = mockk<Context>(relaxed = true).apply {
        every { getDatabasePath(any()) } returns dbFile
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Writes a file that looks exactly like one a v1 build at [version] would have left behind. */
    private fun createDatabase(version: Int): SQLiteConnection {
        val schema = json
            .parseToJsonElement(File("src/test/resources/legacy-schemas/$version.json").readText())
            .jsonObject
            .getValue("database")
            .jsonObject

        return BundledSQLiteDriver().open(dbFile.absolutePath).apply {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                execSQL(entity.jsonObject.createSqlFor(table))
                entity.jsonObject.getValue("indices").jsonArray.forEach { index ->
                    execSQL(index.jsonObject.createSqlFor(table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { execSQL(it.jsonPrimitive.content) }
            execSQL("PRAGMA user_version = $version")
        }
    }

    private fun JsonObject.createSqlFor(tableName: String): String =
        getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)

    private fun openMigrated(): LegacyDatabase =
        Room.databaseBuilder(context, LegacyDatabase::class.java, dbFile.name)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /** Every row rendered as `title|data`, so an unchanged file compares equal with a clear diff. */
    private fun rowSnapshot(): List<String> =
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.prepare("SELECT title, data FROM SecureElement ORDER BY id").use { stmt ->
                buildList {
                    while (stmt.step()) {
                        val title = if (stmt.isNull(0)) "<null>" else stmt.getText(0)
                        val data = if (stmt.isNull(1)) "<null>" else stmt.getBlob(1).joinToString()
                        add("$title|$data")
                    }
                }
            }
        }

    @Test
    fun `repairs a null title so the file opens with an empty one`() = runTest {
        createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES (NULL, x'0102', 1)",
            )
        }

        assertEquals(1, sanitizer.sanitize(dbFile.absolutePath))

        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(1, rows.size)
        assertEquals("", rows.single().element.title)
        assertContentEquals(byteArrayOf(1, 2), rows.single().element.data)
    }

    @Test
    fun `repairs null data so the row survives with an empty blob`() = runTest {
        createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES ('Has title', NULL, 1)",
            )
        }

        assertEquals(1, sanitizer.sanitize(dbFile.absolutePath))

        val db = openMigrated()
        val rows = db.legacyElementDao().getAllWithTags()
        db.close()

        assertEquals(1, rows.size)
        assertEquals("Has title", rows.single().element.title)
        // Kept rather than deleted on purpose: an empty blob cannot decrypt, so the import reports
        // this as one failed row instead of losing the whole file.
        assertTrue(rows.single().element.data.isEmpty())
    }

    @Test
    fun `returns zero and creates nothing when the file is missing`() {
        assertFalse(dbFile.exists())

        assertEquals(0, sanitizer.sanitize(dbFile.absolutePath))

        assertFalse(dbFile.exists(), "sanitizing must never bring a legacy database into existence")
    }

    @Test
    fun `returns zero and leaves a version 3 file untouched`() {
        createDatabase(3).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, favorite, type) " +
                    "VALUES ('Kept', x'0909', 0, 1)",
            )
        }
        val before = rowSnapshot()

        assertEquals(0, sanitizer.sanitize(dbFile.absolutePath))

        assertEquals(before, rowSnapshot())
    }

    @Test
    fun `returns zero when the file has no SecureElement table`() {
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE Leftover (id INTEGER PRIMARY KEY)")
            connection.execSQL("PRAGMA user_version = 2")
        }

        assertEquals(0, sanitizer.sanitize(dbFile.absolutePath))
    }

    @Test
    fun `returns zero and changes nothing when no column is null`() {
        createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES ('Clean', x'0102', 1)",
            )
        }
        val before = rowSnapshot()

        assertEquals(0, sanitizer.sanitize(dbFile.absolutePath))

        assertEquals(before, rowSnapshot())
    }
}
