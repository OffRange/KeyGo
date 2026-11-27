package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.Device
import de.davisalessandro.keygo.rust.SyncDevice
import java.util.UUID
import kotlin.time.Instant

internal fun SyncDevice.toDomain(currentDeviceId: UUID) = Device(
    id = id,
    name = name,
    platform = platform,
    appVersion = appVersion,
    createdAt = Instant.fromEpochSeconds(createdAt.toLong()),
    lastSeenAt = Instant.fromEpochSeconds(lastSeenAt.toLong()),
    authTime = authTime?.let { Instant.fromEpochSeconds(it.toLong()) },
    loggedIn = loggedIn,
    current = id == currentDeviceId,
)
