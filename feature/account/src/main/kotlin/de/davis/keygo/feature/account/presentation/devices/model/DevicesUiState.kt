package de.davis.keygo.feature.account.presentation.devices.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class DevicesUiState(
    val devices: DevicesState = DevicesState.Loading,
    val isRefreshing: Boolean = false,
)
