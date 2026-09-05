package de.davis.keygo.core.security.time

import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.time.SessionClock

class FakeSessionClock(var isExpired: Boolean = false) : SessionClock {

    var active = true
        private set

    override fun markActive() {
        active = true
    }

    override fun markInactive() {
        active = false
    }

    override fun expired(timeout: LockInfo.Timeout): Boolean = isExpired
}
