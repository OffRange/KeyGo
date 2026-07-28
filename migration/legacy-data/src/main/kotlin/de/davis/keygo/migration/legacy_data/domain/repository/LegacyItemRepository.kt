package de.davis.keygo.migration.legacy_data.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure

/** What the inherited database file turned out to be. */
internal sealed interface LegacyDatabaseState {

    /** No file at `secure_element_database`. */
    data object Absent : LegacyDatabaseState

    /**
     * A file exists, opened, and provably has no `SecureElement` table. On a developer device this
     * is a leftover v2 database from before `ItemDatabase` was renamed to `keygo_database`. It
     * holds no v1 data, so it is deleted rather than read.
     *
     * "Provably" is the whole of it. This is the one state that destroys the file, so it is only
     * ever reached by looking for the table and not finding it, never by reading a failure as if it
     * meant the table was gone.
     */
    data object NotLegacy : LegacyDatabaseState

    /**
     * A file exists but cannot be opened at all: corrupt, truncated, or not a SQLite database.
     *
     * Kept apart from [NotLegacy] on purpose. A file that will not open tells us nothing about what
     * is inside it, and [NotLegacy] is the state that gets the file deleted. A partially restored
     * backup deserves to be left where it is, not thrown away on a guess.
     */
    data object Unreadable : LegacyDatabaseState

    data object Present : LegacyDatabaseState
}

internal data class LegacyReadResult(
    val items: List<LegacyItem>,
    val failures: List<LegacyRowFailure>,
)

/** Locates and removes the legacy database file. Keeps `Context` out of the reader. */
internal interface LegacyDatabaseFiles {

    fun exists(): Boolean

    /** Deletes the database and its `-wal`, `-shm` and `-journal` siblings. */
    fun delete(): Boolean
}

/**
 * Reads the inherited v1 database.
 *
 * [state] is the gate and has to be asked first. Everything below it opens the file, and Room
 * creates a file it is asked to open, so reading on an install that never had a v1 database would
 * leave an empty `secure_element_database` behind for every later run to find.
 */
internal interface LegacyItemRepository {

    suspend fun state(): LegacyDatabaseState

    /**
     * Rows the sanitizer repaired to make the inherited file openable at all, so the migration can
     * tell the user that some of what it imported was patched up on the way in.
     */
    val repairedRows: Int

    /**
     * Reads, decrypts and parses every row.
     *
     * A row that fails is reported in [LegacyReadResult.failures] and never thrown. Only something
     * that stops the whole run before any row can be judged, such as a gone Keystore alias, comes
     * back as a failed [Result].
     */
    suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure>

    /** Removes the rows that were successfully imported, so a retry cannot duplicate them. */
    suspend fun prune(legacyIds: List<Long>): Result<Unit, LegacyReadFailure>

    suspend fun remainingCount(): Result<Int, LegacyReadFailure>

    /** Closes the database and deletes the file. */
    fun deleteDatabase(): Boolean
}
