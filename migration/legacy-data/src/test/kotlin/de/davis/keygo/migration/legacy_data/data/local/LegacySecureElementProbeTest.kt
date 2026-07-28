package de.davis.keygo.migration.legacy_data.data.local

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacySecureElementProbe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The probe is what stands between a file that cannot be read and a file that gets deleted, so what
 * matters here is which answers it is willing to give. False is the destructive one, and only a
 * file it actually opened and looked inside may produce it.
 */
class LegacySecureElementProbeTest {

    private val tempDir: File = java.nio.file.Files.createTempDirectory("legacy-probe").toFile()
    private val dbFile: File = File(tempDir, "secure_element_database")

    /**
     * The probe turns the database name into a path with `Context.getDatabasePath`. A fully relaxed
     * mock answers that with an empty path, which would point it at some other file entirely.
     */
    private val context: Context = mockk<Context>(relaxed = true).apply {
        every { getDatabasePath(any()) } returns dbFile
    }

    private val probe = AndroidLegacySecureElementProbe(context, BundledSQLiteDriver())

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `finds the table in a file a v1 build left behind`() {
        seedLegacyDatabase(dbFile, version = 3).close()

        assertEquals(true, probe.hasSecureElementTable())
    }

    @Test
    fun `finds the table in the oldest v1 file, before either auto-migration`() {
        seedLegacyDatabase(dbFile, version = 1).close()

        assertEquals(true, probe.hasSecureElementTable())
        assertEquals(1, userVersionOf(dbFile), "the probe must not migrate the file it reads")
    }

    /**
     * The leftover v2 database from before `ItemDatabase` was renamed. This is the only answer that
     * gets a file deleted, and this is the only shape of file allowed to produce it.
     */
    @Test
    fun `reports no table for a database that is not v1's`() {
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE Leftover (id INTEGER PRIMARY KEY)")
        }

        assertEquals(false, probe.hasSecureElementTable())
    }

    /**
     * Null, never false. A partially restored backup is an ordinary thing to find on a migration
     * path, and it says nothing about whether v1's data is in there. Answering false would hand the
     * caller a verdict that deletes it.
     */
    @Test
    fun `answers nothing for a file that is not a database at all`() {
        dbFile.writeText("this is not a sqlite database, it is a text file that got restored here")

        assertNull(probe.hasSecureElementTable())
    }

    @Test
    fun `answers nothing and creates nothing when there is no file`() {
        assertFalse(dbFile.exists())

        assertNull(probe.hasSecureElementTable())

        assertFalse(dbFile.exists(), "probing must never bring a legacy database into existence")
    }

    /** A probe that rewrites the file it is asked about would be a one-way door of its own. */
    @Test
    fun `leaves the rows it reads over alone`() {
        seedLegacyDatabase(dbFile, version = 2).use { connection ->
            connection.execSQL(
                "INSERT INTO SecureElement (title, data, type) VALUES (NULL, x'0102', 1)",
            )
        }

        assertEquals(true, probe.hasSecureElementTable())

        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.prepare("SELECT title FROM SecureElement").use { stmt ->
                assertTrue(stmt.step())
                assertTrue(stmt.isNull(0), "the probe repaired a row it was only asked to look at")
            }
        }
        assertEquals(2, userVersionOf(dbFile))
    }
}
