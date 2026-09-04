package de.davis.keygo.core.security.data.mapper

import de.davis.keygo.core.security.data.local.model.ProtoLockInfo
import de.davis.keygo.core.security.domain.model.LockInfo

internal fun ProtoLockInfo.toDomain() = LockInfo(
    autoLockTimeout = autoLockTimeout.toDomain(),
    backgroundedAt = backgroundedAt
)

internal fun LockInfo.Timeout.toProto() = ProtoLockInfo.LockTimeout.entries[ordinal]

/**
 * Maps the [ProtoLockInfo.LockTimeout] to the corresponding [LockInfo.Timeout]. The entries
 * must be exactly in the same order.
 */
private fun ProtoLockInfo.LockTimeout.toDomain() = LockInfo.Timeout.entries[ordinal]