package de.davis.keygo.legacy_migration.domain.model

/**
 * How a call to `RunPendingMigrationUseCase` ended, in the terms the auth screen has to act on.
 */
sealed interface MigrationResult {

    /**
     * No v1 marker: a clean install, or a migration that already finished. Nothing was opened, and
     * this is the answer for the overwhelming majority of unlocks.
     */
    data object NotPending : MigrationResult

    /**
     * The import reached a verdict about the v1 file and the marker is gone. [skippedItems] counts
     * rows that could not be read; those rows are still in the legacy file, which
     * `MigrateLegacyDataUseCase` retains whenever anything failed.
     */
    data class Completed(val skippedItems: Int) : MigrationResult

    /**
     * The import could not reach a verdict. The marker is kept, the legacy file is untouched, and
     * the next attempt retries.
     */
    data class Incomplete(val cause: Throwable) : MigrationResult
}
