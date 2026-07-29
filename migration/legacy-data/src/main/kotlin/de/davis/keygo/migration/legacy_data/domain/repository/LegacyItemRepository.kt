package de.davis.keygo.migration.legacy_data.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure

internal data class LegacyReadResult(
    val items: List<LegacyItem>,
    val failures: List<LegacyRowFailure>,
)

/** Reads the inherited v1 database. */
internal interface LegacyItemRepository {

    /**
     * Reads, decrypts and parses every row.
     *
     * A row that fails is reported in [LegacyReadResult.failures] and never thrown. Only something
     * that stops the whole run before any row can be judged, such as a gone Keystore alias, comes
     * back as a failed [Result].
     *
     * This is also the gate on the file's existence. A count is taken before anything else, and a
     * file with no rows in it comes back as [LegacyReadFailure.DatabaseEmpty], which is the one
     * failure the caller answers by deleting. Every other failure means the file was not understood,
     * and a file nobody could read has to be left where it was found.
     */
    suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure>

    /** Removes the rows that were successfully imported, so a retry cannot duplicate them. */
    suspend fun prune(legacyIds: List<Long>): Result<Unit, LegacyReadFailure>

    suspend fun remainingCount(): Result<Int, LegacyReadFailure>

    /** Closes the database and deletes the file. */
    fun deleteDatabase(): Boolean
}
