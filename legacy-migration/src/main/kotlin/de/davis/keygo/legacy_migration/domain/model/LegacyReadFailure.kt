package de.davis.keygo.legacy_migration.domain.model

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
     * it as one [LegacyFailureReason.Unreadable] per row would tell the user that every entry
     * was individually damaged, when in fact the entries are intact and only the key that opens
     * them is gone. The two cases also call for opposite handling: this one stops the run.
     */
    KeyUnavailable,

    /**
     * The file exists but cannot be opened at all: corrupt, truncated, or not a SQLite database. A
     * partially restored backup, or a foreign file sitting at `secure_element_database`, lands here.
     */
    DatabaseUnreadable,

    /**
     * There is no file, or the file opened and provably holds no v1 rows: either it was never
     * written to, or an earlier run already imported and pruned everything in it.
     *
     * Kept apart from [DatabaseUnreadable] on purpose, and that distinction carries the whole
     * safety of this path. This is the one failure that gets the file deleted, so it is only ever
     * reached by counting the rows and getting zero. A file that will not open tells us nothing
     * about what is inside it, and a partially restored backup deserves to be left where it is
     * rather than thrown away on a guess.
     */
    DatabaseEmpty,
}
