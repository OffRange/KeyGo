package de.davis.keygo.core.security.data.mapper

import de.davis.keygo.core.security.data.local.model.ProtoLockInfo
import de.davis.keygo.core.security.domain.model.LockInfo

internal fun ProtoLockInfo.toDomain() = LockInfo(
    autoLockTimeout = autoLockTimeout.toDomain(),
)

internal fun LockInfo.Timeout.toProto() = when (this) {
    LockInfo.Timeout.IMMEDIATELY -> ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_IMMEDIATELY
    LockInfo.Timeout.ONE_MINUTE -> ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_ONE_MINUTE
    LockInfo.Timeout.TWO_MINUTES -> ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_TWO_MINUTES
    LockInfo.Timeout.FIVE_MINUTES -> ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_FIVE_MINUTES
}

private fun ProtoLockInfo.LockTimeout.toDomain() = when (this) {
    ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_IMMEDIATELY -> LockInfo.Timeout.IMMEDIATELY
    ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_ONE_MINUTE -> LockInfo.Timeout.ONE_MINUTE
    ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_TWO_MINUTES -> LockInfo.Timeout.TWO_MINUTES
    ProtoLockInfo.LockTimeout.LOCK_TIMEOUT_FIVE_MINUTES -> LockInfo.Timeout.FIVE_MINUTES
    ProtoLockInfo.LockTimeout.UNRECOGNIZED -> LockInfo.Timeout.IMMEDIATELY
}
