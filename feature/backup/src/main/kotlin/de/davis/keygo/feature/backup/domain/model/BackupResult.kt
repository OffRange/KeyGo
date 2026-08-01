package de.davis.keygo.feature.backup.domain.model

sealed interface BackupResult {
    data object Success : BackupResult

    /** @param reason why the run failed, null when it predates this field or was not recorded */
    data class Failure(val reason: BackupFailureReason? = null) : BackupResult
}
