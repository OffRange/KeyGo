package de.davis.keygo.migration.legacy_data.data.local.datasource

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/**
 * Repairs the rows in an inherited v1 file that would otherwise abort Room's 2-to-3 recreate.
 *
 * Versions 1 and 2 declare `title` and `data` nullable while version 3 declares both NOT NULL, and
 * the generated recreate copies rows across with a plain `INSERT ... SELECT`. A single row carrying
 * a real NULL therefore makes the whole file unopenable, which costs the user every other row as
 * well. `LegacyDatabaseOpenTest` pins that hazard so it stays visible.
 *
 * The repair is deliberately the smallest thing that clears the constraint: an empty title and an
 * empty blob, never a deletion and never an invented placeholder. An empty blob cannot decrypt, so
 * the per-row skip policy reports such a row as one failed item instead of the whole import dying.
 *
 * Running before Room opens the file leaves the auto-migrations and the identity hash untouched.
 */
internal class LegacyDatabaseSanitizer(
    private val driver: SQLiteDriver = AndroidSQLiteDriver(),
) {

    /**
     * Repairs [path] and returns the number of rows it touched, so the migration can report it.
     * Returns 0 and changes nothing when there is nothing to repair.
     */
    fun sanitize(path: String): Int {
        // Opening read-write creates the file, which would make a clean v2 install look like it
        // inherited a legacy database.
        if (!File(path).exists()) return 0

        return driver.open(path).use { connection ->
            // A version 3 file already survived the recreate, and its columns are NOT NULL anyway.
            if (connection.userVersion() >= 3) return@use 0
            // No SecureElement means this is not a v1 file. Leave it alone rather than throw.
            if (!connection.hasTable("SecureElement")) return@use 0

            val repaired = connection.countRepairableRows()
            connection.execSQL("UPDATE SecureElement SET title = '' WHERE title IS NULL")
            connection.execSQL("UPDATE SecureElement SET data = x'' WHERE data IS NULL")
            repaired
        }
    }

    private fun SQLiteConnection.userVersion(): Int = selectInt("PRAGMA user_version")

    private fun SQLiteConnection.hasTable(name: String): Boolean =
        selectInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'") > 0

    /**
     * Counted before the updates rather than summed from their change counts, so a row that is null
     * in both columns is reported once instead of twice.
     */
    private fun SQLiteConnection.countRepairableRows(): Int =
        selectInt("SELECT COUNT(*) FROM SecureElement WHERE title IS NULL OR data IS NULL")

    private fun SQLiteConnection.selectInt(sql: String): Int =
        prepare(sql).use { statement ->
            statement.step()
            statement.getLong(0).toInt()
        }
}
