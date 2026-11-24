package de.davis.keygo.feature.account.presentation.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.feature.account.presentation.devices.mapper.toDevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class DevicesStateProvider : PreviewParameterProvider<DevicesState> {

    private val now = Clock.System.now()

    private val states = listOf(
        // Every row style: this device, active now, active a while ago, signed out.
        "Mixed" to listOf(
            device("Pixel 9", current = true),
            device("Pixel 7", lastSeenAt = now - 30.seconds),
            device("Galaxy Tab S9", lastSeenAt = now - 3.hours),
            device("Pixel 8 Pro", lastSeenAt = now - 6.days, loggedIn = false),
        ).toDevicesState(now),
        // Only this device: the "Other devices" section is left out.
        "Only this device" to listOf(device("Pixel 9", current = true)).toDevicesState(now),
        "Loading" to DevicesState.Loading,
        "Failed" to DevicesState.Failed,
    )

    override val values = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first

    private fun device(
        name: String,
        lastSeenAt: Instant = now,
        loggedIn: Boolean = true,
        current: Boolean = false,
    ) = Device(
        id = UUID.randomUUID(),
        name = name,
        platform = "android",
        appVersion = "2.2.0",
        createdAt = now - 30.days,
        lastSeenAt = lastSeenAt,
        authTime = null,
        loggedIn = loggedIn,
        current = current,
    )
}
