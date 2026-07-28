package de.davis.keygo.migration.legacy_data.data.local.datasource

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * Answers the one question that can prove an inherited file holds no v1 data: is v1's
 * `SecureElement` table there?
 *
 * Named and kept apart because that answer is what gets the file deleted. It has to be asked of the
 * closed file, before Room is allowed to run the 2-to-3 recreate, and it has to be asked directly.
 * Inferring it from a failed query cannot work: a corrupt page, a disk that filled up during the
 * recreate and a cancelled run all fail the same way a foreign file does, and reading any of those
 * as "not v1's" throws the user's only copy of their data away on a guess.
 */
internal interface LegacySecureElementProbe {

    /**
     * True when the table is there, false when the file opened and the table provably is not, and
     * null when the file could not be inspected at all.
     *
     * Null is not a no. A file that will not open tells us nothing about what is inside it, so a
     * caller deciding whether to delete has to treat null the way it treats true.
     */
    fun hasSecureElementTable(): Boolean?
}

/**
 * Reads the answer off the file itself, with no Room in the way.
 *
 * @param driver left at the framework helper in production, which is what the rest of this module
 * opens files with. A JVM test has no framework helper available, so it passes a bundled driver.
 */
internal class AndroidLegacySecureElementProbe(
    private val context: Context,
    private val driver: SQLiteDriver = AndroidSQLiteDriver(),
) : LegacySecureElementProbe {

    override fun hasSecureElementTable(): Boolean? {
        val path = context.getDatabasePath(LEGACY_DATABASE_NAME)
        // Opening read-write creates the file, which would make a clean v2 install look like it
        // inherited a legacy database. Null rather than false, because a file that is not there
        // says nothing about the file this probe was asked about.
        if (!path.exists()) return null

        return try {
            driver.open(path.absolutePath).use { it.hasSecureElementTable() }
        } catch (_: Exception) {
            // Corrupt, truncated, or not a SQLite database at all. Nothing was learned about what
            // is inside it, and answering "no table" here is exactly the guess that costs a user
            // with a half restored backup everything they had.
            null
        }
    }
}

/**
 * Shared with [LegacyDatabaseSanitizer], which asks the same question of a connection it is already
 * holding open. One copy, so the two can never drift into disagreeing about what a v1 file is.
 *
 * Not parameterized by table name: the callers are compile-time constants, and a String parameter
 * here would invite an unescaped literal later.
 */
internal fun SQLiteConnection.hasSecureElementTable(): Boolean =
    selectInt(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'SecureElement'",
    ) > 0

/** Every call site is a COUNT(*) or a PRAGMA, both of which always return exactly one row. */
internal fun SQLiteConnection.selectInt(sql: String): Int =
    prepare(sql).use { statement ->
        check(statement.step()) { "expected a row from: $sql" }
        statement.getLong(0).toInt()
    }
