package de.davis.keygo.legacy_migration.domain.model

sealed interface LegacyMigrationOutcome {

    /**
     * Whether a later run would have nothing left to find. False is what keeps the retry alive on
     * the next unlock, and it is the answer for every ending that leaves v1 rows on disk.
     */
    val nothingLeftToImport: Boolean

    /** No legacy file, or one that opened and held no v1 rows. Either way it is gone now. */
    data object NothingToMigrate : LegacyMigrationOutcome {
        override val nothingLeftToImport = true
    }

    data class Migrated(val report: LegacyMigrationReport) : LegacyMigrationOutcome {

        // A retained file is a file with rows still in it: something was skipped, or the prune or
        // the delete did not go through. All three are what the next unlock is for.
        override val nothingLeftToImport get() = !report.fileRetained
    }

    /** The run could not start or the batch write failed as a whole. Nothing was imported. */
    data class Failed(val cause: Throwable) : LegacyMigrationOutcome {
        override val nothingLeftToImport = false
    }
}

/**
 * Why a whole run stopped. Carried by [LegacyMigrationOutcome.Failed], which never reports a
 * partial import: no row was written to v2. The legacy file itself may already have gone through
 * Room's one-way 1/2-to-3 recreate by this point, since that runs on the first query against the
 * file and the read issues one before anything else; that recreate only drops a table v2 never
 * reads and carries every v1 row across, so it costs nothing this run would have protected anyway.
 */
class LegacyMigrationException internal constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
