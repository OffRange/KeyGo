package de.davis.keygo.feature.backup.presentation.hub.model

internal sealed interface BackupHubEvent {
    data object NavigateToExport : BackupHubEvent
    data object NavigateToImport : BackupHubEvent
}