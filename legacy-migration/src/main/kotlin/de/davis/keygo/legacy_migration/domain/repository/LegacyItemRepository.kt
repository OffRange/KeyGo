package de.davis.keygo.legacy_migration.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.legacy_migration.domain.model.LegacyItem
import de.davis.keygo.legacy_migration.domain.model.LegacyReadFailure
import de.davis.keygo.legacy_migration.domain.model.LegacyRowFailure
import javax.crypto.SecretKey

internal data class LegacyReadResult(
    val items: List<LegacyItem>,
    val failures: List<LegacyRowFailure>,

    /**
     * v1's key, resolved once for the whole run. Carried here because the rows are handed over with
     * their nested password blobs still sealed, and whatever opens those has to use the same key
     * the rows themselves were read under.
     */
    val legacyKey: SecretKey,
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

    suspend fun deleteDatabase(): Boolean
}
