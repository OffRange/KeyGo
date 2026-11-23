package de.davis.keygo.feature.account.presentation.models

internal sealed interface DevicesState {
    data object Loading : DevicesState
    data class Loaded(val summary: DeviceSummary) : DevicesState
    data object Failed : DevicesState
}
