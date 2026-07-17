package de.davis.keygo.feature.backup.presentation.hub.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup

@Immutable
internal data class BackupGroup(
    val state: DispatchedBackup.State,
    val items: List<DispatchedBackup>,
)
