package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable

@Stable
internal data class SelectDestinationState(
    val destination: BackupDestination? = null,
)