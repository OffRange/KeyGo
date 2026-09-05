package de.davis.keygo.core.security.data

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import de.davis.keygo.core.security.FakeLockInfoRepository
import de.davis.keygo.core.security.data.time.SessionClockImpl
import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.time.FakeElapsedTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class SessionLockObserverTest {

    private val context = RuntimeEnvironment.getApplication()
    private val session = SessionImpl().apply { startSession(ByteArray(32) { it.toByte() }) }
    private val handoff = SystemHandoffImpl()
    private val time = FakeElapsedTimeProvider()
    private val clock = SessionClockImpl(time)
    private val lockInfoRepository = FakeLockInfoRepository()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val owner = StubLifecycleOwner()

    private val fiveMinutes = LockInfo.Timeout.FIVE_MINUTES.duration.inWholeMilliseconds

    private fun observer(
        timeout: LockInfo.Timeout = LockInfo.Timeout.IMMEDIATELY,
    ): SessionLockObserver {
        lockInfoRepository.lockInfo = LockInfo(autoLockTimeout = timeout)
        return SessionLockObserver(context, session, handoff, clock, scope, lockInfoRepository)
    }

    /**
     * Moves the session clock and virtual time together, so a scheduled wipe both fires and sees
     * the time it was waiting for. Tests that advance `time` alone are checking the onStart path
     * in isolation, as it behaves when a frozen process held the timer past its delay.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun elapse(millis: Long) {
        time.advanceBy(millis)
        scope.testScheduler.advanceTimeBy(millis)
        scope.testScheduler.runCurrent()
    }

    private fun screenOff() {
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `backgrounding ends the session`() {
        val observer = observer()

        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `backgrounding for a system screen we launched keeps the session`() {
        val observer = observer()
        handoff.expectReturn()

        observer.onStop(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a handoff covers one round trip, not the background after it`() {
        val observer = observer()
        handoff.expectReturn()
        observer.onStop(owner)
        observer.onStart(owner)

        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `the screen going off during a handoff ends the session`() {
        val observer = observer()
        handoff.expectReturn()
        observer.onStop(owner)

        screenOff()

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `the screen stops being watched once the app is back in the foreground`() {
        val observer = observer()
        handoff.expectReturn()
        observer.onStop(owner)
        observer.onStart(owner)

        screenOff()

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `backgrounding under a timeout keeps the session`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)

        observer.onStop(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `returning within the timeout keeps the session`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        observer.onStop(owner)

        time.advanceBy(fiveMinutes - 1)
        observer.onStart(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `returning after the timeout ends the session`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        observer.onStop(owner)

        time.advanceBy(fiveMinutes)
        observer.onStart(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `a slow handoff is not timed against the auto lock timeout`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        handoff.expectReturn()
        observer.onStop(owner)

        time.advanceBy(fiveMinutes * 2)
        observer.onStart(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a backgrounded session is wiped once the timeout passes`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)

        observer.onStop(owner)
        elapse(fiveMinutes)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `the wipe does not fire before the timeout`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)

        observer.onStop(owner)
        elapse(fiveMinutes - 1)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `returning to the foreground cancels the pending wipe`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        observer.onStop(owner)
        elapse(fiveMinutes - 1)

        observer.onStart(owner)
        elapse(fiveMinutes)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a handoff schedules no wipe`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        handoff.expectReturn()

        observer.onStop(owner)
        elapse(fiveMinutes * 2)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a new session is not judged by the stamp of the one before it`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        observer.onStop(owner)
        time.advanceBy(fiveMinutes * 2)
        observer.onStart(owner)

        session.startSession(ByteArray(32) { it.toByte() })
        observer.onStart(owner)

        assertEquals(true, session.isActive.value)
    }
}

private class StubLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
}
