package de.davis.keygo.core.security.data

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
    private val observer = SessionLockObserver(context, session, handoff)
    private val owner = StubLifecycleOwner()

    private fun screenOff() {
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `backgrounding ends the session`() {
        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `backgrounding for a system screen we launched keeps the session`() {
        handoff.expectReturn()

        observer.onStop(owner)

        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `a handoff covers one round trip, not the background after it`() {
        handoff.expectReturn()
        observer.onStop(owner)
        observer.onStart(owner)

        observer.onStop(owner)

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `the screen going off during a handoff ends the session`() {
        handoff.expectReturn()
        observer.onStop(owner)

        screenOff()

        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `the screen stops being watched once the app is back in the foreground`() {
        handoff.expectReturn()
        observer.onStop(owner)
        observer.onStart(owner)

        screenOff()

        assertEquals(true, session.isActive.value)
    }
}

private class StubLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
}
