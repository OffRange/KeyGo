package de.davis.keygo.feature.account.presentation.devices.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.sync.domain.model.Device

@Immutable
internal sealed interface DevicesState {

    data object Loading : DevicesState

    data class Loaded(
        val current: DeviceItem?,
        val others: List<DeviceItem>,
    ) : DevicesState

    data object Failed : DevicesState
}

@Immutable
internal data class DeviceItem(
    val device: Device,
    val activity: DeviceActivity,
)
