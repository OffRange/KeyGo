package de.davis.keygo.feature.account.presentation.devices.model

import java.util.UUID

internal sealed interface DevicesUiEvent {
    data object OnRetryClicked : DevicesUiEvent
    data object OnRefresh : DevicesUiEvent
    data object OnResumed : DevicesUiEvent

    data class OnSignOutRequest(val deviceId: UUID) : DevicesUiEvent
}
