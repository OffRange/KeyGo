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
import org.koin.core.annotation.Single

@Single(createdAtStart = true)
internal class SessionLockObserver(
    private val context: Context,
    private val session: Session,
    private val handoff: SystemHandoff,
) : DefaultLifecycleObserver {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = endSession()
    }

    private var watchingScreenOff = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        stopWatchingScreenOff()
        handoff.clear()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (handoff.isPending) watchScreenOff()
        else session.endSession()
    }

    private fun endSession() {
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
