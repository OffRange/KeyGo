package de.davis.keygo.core.security.data.time

import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.time.ElapsedTimeProvider
import de.davis.keygo.core.security.domain.time.SessionClock
import org.koin.core.annotation.Single

@Single
internal class SessionClockImpl(
    private val timeProvider: ElapsedTimeProvider,
) : SessionClock {

    // Written on the main thread from the process lifecycle callbacks, read from there and from the
    // scheduled wipe on the app scope's dispatcher.
    @Volatile
    private var backgroundedAt: Long? = null

    override fun markActive() {
        backgroundedAt = null
    }

    override fun markInactive() {
        backgroundedAt = timeProvider.elapsedTime()
    }

    override fun expired(timeout: LockInfo.Timeout): Boolean {
        val since = backgroundedAt ?: return false
        return timeProvider.elapsedTime() - since >= timeout.duration.inWholeMilliseconds
    }
}
