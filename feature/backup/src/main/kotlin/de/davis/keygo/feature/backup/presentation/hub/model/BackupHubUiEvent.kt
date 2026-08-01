package de.davis.keygo.feature.backup.presentation.hub.model

import de.davis.keygo.feature.backup.domain.model.DispatchedBackup

internal sealed interface BackupHubUiEvent {
    data object OnScheduleBackupClick : BackupHubUiEvent
    data object OnRestoreBackup : BackupHubUiEvent
    data class OnCancelBackup(val id: String, val kind: DispatchedBackup.Kind) : BackupHubUiEvent
}
