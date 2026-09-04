package de.davis.keygo.core.security.domain.model

data class LockInfo(
    val autoLockTimeout: Timeout,
    val backgroundedAt: Long,
) {
    enum class Timeout {
        IMMEDIATELY,
        ONE_MINUTE,
        TWO_MINUTES,
        FIVE_MINUTES,
    }
}
