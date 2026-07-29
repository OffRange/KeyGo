package de.davis.keygo.migration.legacy_data.domain.model

data class LegacyRowFailure(
    val legacyId: Long,
    val title: String,
    val reason: LegacyFailureReason,
)

data class LegacyMigrationReport(
    val migratedItems: Int,
    val failures: List<LegacyRowFailure>,

    /**
     * True when the run ended with the legacy file still on disk despite every row it looked at
     * having imported cleanly. [hasFailures] alone would miss this: a prune, a recount, or a delete
     * that failed for reasons that have nothing to do with any one row still leaves a file behind
     * that reimports the whole vault on the next unlock, and that has to be reported just as loudly
     * as a row failure is.
     */
    val fileRetained: Boolean = false,
) {
    val hasFailures: Boolean get() = failures.isNotEmpty()
}
