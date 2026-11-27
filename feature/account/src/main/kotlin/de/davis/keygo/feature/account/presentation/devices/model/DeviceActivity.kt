package de.davis.keygo.feature.account.presentation.devices.model

import kotlin.time.Instant

internal sealed interface DeviceActivity {

    data object ThisDevice : DeviceActivity
    data object ActiveNow : DeviceActivity

    data class ActiveAt(val lastSeenAt: Instant, val now: Instant) : DeviceActivity

    data object SignedOut : DeviceActivity
}
