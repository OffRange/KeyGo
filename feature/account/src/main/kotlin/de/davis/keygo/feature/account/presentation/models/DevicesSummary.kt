package de.davis.keygo.feature.account.presentation.models

internal sealed interface DevicesSummary {

    data object Loading : DevicesSummary
    data class Loaded(
        val total: Int,
        val signedIn: Int,
    ) : DevicesSummary

    data object Failed : DevicesSummary
}
