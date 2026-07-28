package de.davis.keygo.migration.legacy_data.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseSanitizer
import de.davis.keygo.migration.legacy_data.data.local.datasource.SanitizingLegacyDatabaseProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The provider is the only place that can repair the inherited file, because it is the only place
 * that still holds it closed. Room runs the 2-to-3 recreate on the first query, and one NULL title
 * or blob aborts it and takes every other row down with it, so a repair that lands after the open
 * lands too late to be worth anything.
 *
 * These tests therefore assert on the file itself rather than on what Room later reads out of it:
 * that is the only way to see that the repair happened while the file was still at version 2.
 */
class SanitizingLegacyDatabaseProviderTest {

    private val tempDir: File = java.nio.file.Files.createTempDirectory("legacy-provider").toFile()
    private val dbFile: File = File(tempDir, "secure_element_database")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Room and the sanitizer both turn a database name into a path with `Context.getDatabasePath`.
     * A fully relaxed mock answers that with an empty path, which would quietly point both of them
     * at some other file, so this one has to resolve to the seeded file.
     */
    private val context: Context = mockk<Context>(relaxed = true).apply {
        every { getDatabasePath(any()) } returns dbFile
    }

    private fun newProvider() = SanitizingLegacyDatabaseProvider(
        context = context,
        sanitizer = LegacyDatabaseSanitizer(BundledSQLiteDriver()),
    )

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

    private fun titlesOnDisk(): List<String> =
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.prepare("SELECT title FROM SecureElement ORDER BY id").use { stmt ->
                buildList {
                    while (stmt.step()) add(if (stmt.isNull(0)) "<null>" else stmt.getText(0))
                }
            }
        }

    private fun userVersion(): Int =
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version").use { stmt ->
                stmt.step()
                stmt.getLong(0).toInt()
            }
        }

    @Test
    fun `repairs the file before the database is built`() {
        createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES (NULL, x'0102', 1)",
            )
        }

        val provider = newProvider()
        assertNotNull(provider.get())

        assertEquals(1, provider.repairedRows)
        assertEquals(listOf(""), titlesOnDisk())
        assertEquals(
            2,
            userVersion(),
            "the file must still be at version 2, which is what proves the repair ran before " +
                "Room ever touched it",
        )
    }

    @Test
    fun `reports no repairs for a file that needs none`() {
        createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES ('Clean', x'0102', 1)",
            )
        }

        val provider = newProvider()
        assertNotNull(provider.get())

        assertEquals(0, provider.repairedRows)
        assertEquals(listOf("Clean"), titlesOnDisk())
    }

    /**
     * A partially restored backup or a foreign file at this path is an ordinary thing to find on a
     * migration path. The sanitizer has no failure channel of its own and throws, so the provider
     * is where that has to stop being an exception and start being an answer.
     */
    @Test
    fun `returns nothing instead of throwing when the file is not a database`() {
        dbFile.writeText("this is not a sqlite database, it is a text file that got restored here")

        assertNull(newProvider().get())
    }

    @Test
    fun `opens nothing more than once until it is closed`() {
        createDatabase(3).close()
        val provider = newProvider()

        val first = assertNotNull(provider.get())
        assertSame(first, provider.get())

        provider.close()

        assertNotSame(first, provider.get())
    }

    /**
     * A clean v2 install has no legacy file, and bringing one into existence here would make the
     * migration believe it inherited a database.
     */
    @Test
    fun `creates no file when there is nothing to migrate`() {
        val provider = newProvider()

        assertNotNull(provider.get())

        assertEquals(0, provider.repairedRows)
        assertFalse(dbFile.exists(), "opening must never bring a legacy database into existence")
    }
}
