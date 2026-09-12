package de.davis.keygo.core.security.data

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import de.davis.keygo.core.security.FakeLockInfoRepository
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.data.time.SessionClockImpl
import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import de.davis.keygo.core.security.time.FakeElapsedTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
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
    private val session = FakeSession(startUnlocked = true)
    private val time = FakeElapsedTimeProvider()
    private val handoff = SystemHandoffImpl()
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
    fun `the screen going off during an ordinary background ends the session`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
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
    fun `a handoff is timed against its grace period, not the auto lock timeout`() {
        val observer = observer(LockInfo.Timeout.ONE_MINUTE)
        handoff.expectReturn()
        observer.onStop(owner)

        time.advanceBy(LockInfo.Timeout.ONE_MINUTE.duration.inWholeMilliseconds)
        observer.onStart(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a handoff that outlasts its grace period ends the session on return`() {
        // The frozen process case: the scheduled wipe never got to run, so the stamp taken on the
        // way out is all that is left to judge the return by. Without it the ARK stays resident for
        // however long the user was gone.
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        handoff.expectReturn()
        observer.onStop(owner)

        time.advanceBy(fiveMinutes * 2)
        observer.onStart(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `a handoff arming outlives at most one background`() {
        // Backing straight out of the screen we opened happens inside the ON_STOP debounce, so no
        // lifecycle callback runs to spend the arming and it is still there at the next background.
        // That one gets the grace; every background after it must not.
        val observer = observer()
        handoff.expectReturn()

        observer.onStop(owner)
        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
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
    fun `a handoff schedules no wipe against the configured timeout`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        handoff.expectReturn()

        observer.onStop(owner)
        elapse(fiveMinutes - 1)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `an abandoned handoff still ends the session once the grace period passes`() {
        // The defect this guards: a handoff that is never returned from (the user never comes
        // back, the screen never turns off) must not hold auto-lock open forever.
        val observer = observer()
        handoff.expectReturn()

        observer.onStop(owner)
        elapse(LockInfo.Timeout.FIVE_MINUTES.duration.inWholeMilliseconds)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `a handoff still within its grace period keeps the session`() {
        val observer = observer()
        handoff.expectReturn()

        observer.onStop(owner)
        elapse(LockInfo.Timeout.FIVE_MINUTES.duration.inWholeMilliseconds - 1)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a new session is not judged by the stamp of the one before it`() {
        val observer = observer(LockInfo.Timeout.FIVE_MINUTES)
        observer.onStop(owner)
        time.advanceBy(fiveMinutes * 2)
        observer.onStart(owner)

        runBlocking { session.unlockWithArk(ByteArray(32) { it.toByte() }) }
        observer.onStart(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a setting that has not been read yet locks rather than lingers`() {
        // The seed stands in until the first read lands. It has to be the locking one: a timeout
        // we do not know yet must not be read as permission to leave the ARK in memory.
        val observer = SessionLockObserver(
            context,
            session,
            handoff,
            clock,
            scope,
            NeverEmittingLockInfoRepository,
        )

        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
    }
}

private object NeverEmittingLockInfoRepository : LockInfoRepository {
    override suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout) = Unit
    override fun observeLockInfo(): Flow<LockInfo> = MutableSharedFlow()
}

private class StubLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
}
