package de.davis.keygo.core.security.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import de.davis.keygo.core.security.domain.Session
import org.koin.core.annotation.Single

/**
 * Ends the session the instant the app leaves the foreground, so returning to it requires
 * re-authentication rather than staying unlocked indefinitely. Registered once per process, the
 * same pattern [de.davis.keygo.feature.backup.domain.BackupEscrowReconciler] already uses for its
 * own process-start hook.
 */
@Single(createdAtStart = true)
internal class SessionLockObserver(
    private val session: Session,
) : DefaultLifecycleObserver {

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        session.endSession()
    }
}
