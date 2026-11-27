package de.davis.keygo.feature.account.presentation.devices.mapper

import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.feature.account.presentation.devices.model.DeviceActivity
import de.davis.keygo.feature.account.presentation.devices.model.DeviceItem
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DevicesStateMapperTest {

    @Test
    fun `a device seen within the last two minutes is active now`() {
        assertEquals(
            DeviceActivity.ActiveNow,
            device(lastSeenAt = now - 90.seconds).activity(now),
        )
    }

    @Test
    fun `a device seen earlier is active at its last sighting`() {
        val lastSeenAt = now - 2.minutes

        assertEquals(
            DeviceActivity.ActiveAt(lastSeenAt, now),
            device(lastSeenAt = lastSeenAt).activity(now),
        )
    }

    @Test
    fun `a signed out device is signed out however recently it was seen`() {
        assertEquals(
            DeviceActivity.SignedOut,
            device(lastSeenAt = now, loggedIn = false).activity(now),
        )
    }

    @Test
    fun `this device is this device however long ago it was seen`() {
        assertEquals(
            DeviceActivity.ThisDevice,
            device(lastSeenAt = now - 1.hours, current = true).activity(now),
        )
    }

    @Test
    fun `devices carry their activity`() {
        val current = device(current = true)
        val signedOut = device(lastSeenAt = now - 1.hours, loggedIn = false)

        assertEquals(
            DevicesState.Loaded(
                current = DeviceItem(current, DeviceActivity.ThisDevice),
                others = listOf(DeviceItem(signedOut, DeviceActivity.SignedOut)),
            ),
            listOf(signedOut, current).toDevicesState(now),
        )
    }

    private companion object {
        val now = Instant.fromEpochSeconds(1_800_000_000)

        fun device(
            lastSeenAt: Instant = now,
            loggedIn: Boolean = true,
            current: Boolean = false,
        ) = Device(
            id = UUID.randomUUID(),
            name = "Pixel",
            platform = "android",
            appVersion = "2.2.0",
            createdAt = Instant.fromEpochSeconds(1_700_000_000),
            lastSeenAt = lastSeenAt,
            authTime = null,
            loggedIn = loggedIn,
            current = current,
        )
    }
}
