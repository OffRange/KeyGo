package de.davis.keygo.feature.backup.domain.model

sealed interface BackupResult {
    data object Success : BackupResult

    /**
     * Backup failed
     *
     * @param reason Carries why the run failed, null when the reason predates this field or wasn't recorded
     */
    data class Failure(val reason: BackupFailureReason? = null) : BackupResult
}
