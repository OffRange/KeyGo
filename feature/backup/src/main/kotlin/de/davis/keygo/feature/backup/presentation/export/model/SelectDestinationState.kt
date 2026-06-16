package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.BackupDestination

@Stable
internal data class SelectDestinationState(
    val destination: BackupDestination? = null,
)