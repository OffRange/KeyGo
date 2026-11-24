package de.davis.keygo.feature.account.presentation.devices.model

internal sealed interface DevicesEvent {
    data object RefreshFailed : DevicesEvent
}
