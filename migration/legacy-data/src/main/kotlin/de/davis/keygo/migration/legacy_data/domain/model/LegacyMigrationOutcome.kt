package de.davis.keygo.migration.legacy_data.domain.model

sealed interface LegacyMigrationOutcome {

    /** No legacy file, or one that opened and held no v1 rows. Either way it is gone now. */
    data object NothingToMigrate : LegacyMigrationOutcome

    data class Migrated(val report: LegacyMigrationReport) : LegacyMigrationOutcome

    /** The run could not start or the batch write failed as a whole. Nothing was imported. */
    data class Failed(val cause: Throwable) : LegacyMigrationOutcome
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
