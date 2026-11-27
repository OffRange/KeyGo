package de.davis.keygo.feature.account.presentation.devices.model

import de.davis.keygo.core.sync.domain.model.SyncError

internal sealed interface DevicesEvent {
    data object RefreshFailed : DevicesEvent
    data class RemoveFailed(val error: SyncError) : DevicesEvent
}
