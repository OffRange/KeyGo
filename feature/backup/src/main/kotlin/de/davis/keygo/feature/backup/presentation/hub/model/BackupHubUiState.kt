package de.davis.keygo.feature.backup.presentation.hub.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.LastBackup

@Stable
internal data class BackupHubUiState(
    val lastBackup: LastBackup? = null,
    val groups: List<BackupGroup> = emptyList(),
) {
    val hasItems: Boolean get() = groups.isNotEmpty()
}
