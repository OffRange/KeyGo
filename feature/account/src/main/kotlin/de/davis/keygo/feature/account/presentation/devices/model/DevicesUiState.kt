package de.davis.keygo.feature.account.presentation.devices.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
internal data class DevicesUiState(
    val devices: DevicesState = DevicesState.Loading,
    val isRefreshing: Boolean = false,
    val removing: Set<UUID> = emptySet(),
)
