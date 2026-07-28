package de.davis.keygo.migration.legacy_data.domain.model

/** Why one v1 row could not be imported. The row stays in the legacy database either way. */
enum class LegacyFailureReason {
    /** The Keystore alias is gone, or the blob does not decrypt under it. */
    Undecryptable,

    /** The blob decrypted but the JSON inside it could not be read. */
    Unparseable,

    /** The JSON carried a `type` that v1 never shipped. */
    UnknownType,

    /** The nested password blob did not decrypt, so the login would have been silently emptied. */
    UndecryptablePassword,

    /** The row converted but the v2 write failed. */
    WriteFailed,
}

/**
 * Why a whole read could not run. Nothing is imported, and the legacy file is left exactly as it
 * was found. That is what separates these from [LegacyFailureReason], which is always about one row
 * among many that were read fine.
 */
internal enum class LegacyReadFailure {

    /**
     * v1's Keystore alias is gone, so no blob in the file can ever be decrypted.
     *
     * Probed once before any row is decrypted rather than inferred from a run of nulls. Reporting
     * it as one [LegacyFailureReason.Undecryptable] per row would tell the user that every entry
     * was individually damaged, when in fact the entries are intact and only the key that opens
     * them is gone. The two cases also call for opposite handling: this one stops the run.
     */
    KeyUnavailable,

    /**
     * The file exists but cannot be opened at all: corrupt, truncated, or not a SQLite database. A
     * partially restored backup, or a foreign file sitting at `secure_element_database`, lands here.
     */
    DatabaseUnreadable,
}

data class LegacyRowFailure(
    val legacyId: Long,
    val title: String,
    val reason: LegacyFailureReason,
)

data class LegacyMigrationReport(
    val migratedItems: Int,
    val failures: List<LegacyRowFailure>,
) {
    val hasFailures: Boolean get() = failures.isNotEmpty()
}

sealed interface LegacyMigrationOutcome {

    /** No legacy file, or a file that turned out not to be v1's. */
    data object NothingToMigrate : LegacyMigrationOutcome

    data class Migrated(val report: LegacyMigrationReport) : LegacyMigrationOutcome

    /** The run could not start or the batch write failed as a whole. Nothing was imported. */
    data class Failed(val cause: Throwable) : LegacyMigrationOutcome
}
