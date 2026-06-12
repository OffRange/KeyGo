package de.davis.keygo.feature.backup.presentation.hub.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.FileFormat

@Stable
internal data class BackupHubUiState(
    val lastBackupAt: String? = null,
    val lastBackupProvider: String? = null,
    val scheduledBackups: List<ScheduledBackup> = emptyList(),
) {
    val hasScheduledItems = scheduledBackups.isNotEmpty()
}

internal data class ScheduledBackup(
    val provider: String,
    val type: FileFormat,
    val scheduleInterval: BackupInterval,
    val path: String,
)
