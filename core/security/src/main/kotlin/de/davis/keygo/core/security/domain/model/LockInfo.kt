package de.davis.keygo.core.security.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

data class LockInfo(
    val autoLockTimeout: Timeout,
) {
    enum class Timeout(val duration: Duration) {
        IMMEDIATELY(0.milliseconds),
        ONE_MINUTE(1.minutes),
        TWO_MINUTES(2.minutes),
        FIVE_MINUTES(5.minutes),
    }
}
