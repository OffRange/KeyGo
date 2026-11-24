package de.davis.keygo.feature.account.presentation.devices.model

internal sealed interface DevicesUiEvent {
    data object OnRetryClicked : DevicesUiEvent
    data object OnRefresh : DevicesUiEvent
    data object OnResumed : DevicesUiEvent
}
