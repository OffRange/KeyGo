package de.davis.keygo.core.security.data.time

import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.time.FakeElapsedTimeProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SessionClockImplTest {

    private val time = FakeElapsedTimeProvider()
    private val clock = SessionClockImpl(time)

    private val oneMinute = LockInfo.Timeout.ONE_MINUTE.duration.inWholeMilliseconds

    @Test
    fun `a session that was never backgrounded does not expire`() {
        time.advanceBy(oneMinute * 10)

        assertFalse(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `a session backgrounded for less than the timeout has not expired`() {
        clock.markInactive()

        time.advanceBy(oneMinute - 1)

        assertFalse(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `a session backgrounded for exactly the timeout has expired`() {
        clock.markInactive()

        time.advanceBy(oneMinute)

        assertTrue(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `a session backgrounded for longer than the timeout has expired`() {
        clock.markInactive()

        time.advanceBy(oneMinute * 5)

        assertTrue(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `IMMEDIATELY expires the moment the session goes inactive`() {
        clock.markInactive()

        assertTrue(clock.expired(LockInfo.Timeout.IMMEDIATELY))
    }

    @Test
    fun `the stamp is taken when the session goes inactive, not when it is read`() {
        time.advanceBy(oneMinute * 10)
        clock.markInactive()

        assertFalse(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `returning to the foreground drops a stamp that had already expired`() {
        clock.markInactive()
        time.advanceBy(oneMinute * 5)

        clock.markActive()

        assertFalse(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }

    @Test
    fun `each background is timed from its own stamp`() {
        clock.markInactive()
        time.advanceBy(oneMinute * 5)
        clock.markActive()

        clock.markInactive()
        time.advanceBy(oneMinute - 1)

        assertFalse(clock.expired(LockInfo.Timeout.ONE_MINUTE))
    }
}
