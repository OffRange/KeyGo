package de.davis.keygo.feature.account.presentation.devices.mapper

import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.feature.account.presentation.devices.model.DeviceActivity
import de.davis.keygo.feature.account.presentation.devices.model.DeviceItem
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val ACTIVE_NOW_WINDOW = 2.minutes

internal fun List<Device>.toDevicesState(now: Instant): DevicesState.Loaded {
    val (current, others) = map { DeviceItem(device = it, activity = it.activity(now)) }
        .partition { it.device.current }
    return DevicesState.Loaded(
        current = current.firstOrNull(),
        others = others.sortedByDescending { it.device.lastSeenAt },
    )
}

internal fun Device.activity(now: Instant): DeviceActivity = when {
    current -> DeviceActivity.ThisDevice
    !loggedIn -> DeviceActivity.SignedOut
    now - lastSeenAt < ACTIVE_NOW_WINDOW -> DeviceActivity.ActiveNow
    else -> DeviceActivity.ActiveAt(lastSeenAt, now)
}
