package de.davis.keygo.feature.backup.presentation.hub.model

internal sealed interface BackupHubUiEvent {
    data object OnScheduleBackupClick : BackupHubUiEvent
    data object OnRestoreBackup : BackupHubUiEvent
}