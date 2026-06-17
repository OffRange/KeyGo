package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

@Stable
internal data class SelectDestinationState(
    val destination: BackupDestination? = null,
    val uri: BackupDestinationUri? = null,
)