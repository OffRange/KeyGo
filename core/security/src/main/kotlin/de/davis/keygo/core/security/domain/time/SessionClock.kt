package de.davis.keygo.core.security.domain.time

import de.davis.keygo.core.security.domain.model.LockInfo

interface SessionClock {

    fun markActive()
    fun markInactive()

    fun expired(timeout: LockInfo.Timeout): Boolean
}
