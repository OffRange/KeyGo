package de.davis.keygo.core.security.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SystemHandoff
import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import de.davis.keygo.core.security.domain.time.SessionClock
import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(createdAtStart = true)
internal class SessionLockObserver(
    private val context: Context,
    private val session: Session,
    private val handoff: SystemHandoff,
    private val sessionClock: SessionClock,
    @param:AppScopeQualifier private val scope: CoroutineScope,
    lockInfoRepository: LockInfoRepository,
) : DefaultLifecycleObserver {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = endSession()
    }

    private var watchingScreenOff = false
    private var lockJob: Job? = null

    private val lockGuard = Any()

    private val autoLockTimeout = lockInfoRepository.observeLockInfo()
        .map { it.autoLockTimeout }
        .stateIn(scope, SharingStarted.Eagerly, LockInfo.Timeout.IMMEDIATELY)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        stopWatchingScreenOff()
        handoff.clear()
        lockJob?.cancel()

        synchronized(lockGuard) {
            // Read before markActive drops the stamp this depends on.
            if (sessionClock.expired(autoLockTimeout.value)) session.endSession()
            sessionClock.markActive()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (handoff.isPending) {
            watchScreenOff()
            scheduleHandoffBackstop()
        } else {
            sessionClock.markInactive()
            val timeout = autoLockTimeout.value
            if (timeout == LockInfo.Timeout.IMMEDIATELY) session.endSession()
            else scheduleWipe(timeout)
        }
    }

    private fun scheduleWipe(timeout: LockInfo.Timeout) {
        lockJob?.cancel()
        lockJob = scope.launch {
            // A frozen or dozing process can hold this past its delay, which is why the check in
            // [onStart] stays the authority. This only ever locks earlier, never later.
            delay(timeout.duration)

            synchronized(lockGuard) {
                // Re-read rather than trusting the cancel alone: a foreground that raced the delay
                // has already dropped the stamp, and must not be locked out by a timer it just beat.
                if (sessionClock.expired(timeout)) session.endSession()
            }
        }
    }

    private fun scheduleHandoffBackstop() {
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(LockInfo.Timeout.FIVE_MINUTES.duration)

            synchronized(lockGuard) {
                if (handoff.isPending) session.endSession()
            }
        }
    }

    private fun endSession() {
        lockJob?.cancel()
        stopWatchingScreenOff()
        handoff.clear()
        session.endSession()
    }

    private fun watchScreenOff() {
        if (watchingScreenOff) return
        ContextCompat.registerReceiver(
            context,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        watchingScreenOff = true
    }

    private fun stopWatchingScreenOff() {
        if (!watchingScreenOff) return
        context.unregisterReceiver(screenOffReceiver)
        watchingScreenOff = false
    }
}
