package de.davis.keygo.core.security.time

import de.davis.keygo.core.security.domain.time.ElapsedTimeProvider

class FakeElapsedTimeProvider(var now: Long = 0L) : ElapsedTimeProvider {

    override fun elapsedTime(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}
