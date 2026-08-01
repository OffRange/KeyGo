package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.core.annotation.Single
import javax.security.auth.DestroyFailedException

@Single
internal class SessionImpl : Session {

    private var _ark: ByteArray? = null

    // Replayed so a collector wired up after an unlock still sees it, and buffered with
    // DROP_OLDEST so emitting can never suspend or fail inside startSession.
    private val _sessionStarts = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val sessionStarts: Flow<Unit> = _sessionStarts

    override val ark: ByteArray
        get() = _ark ?: throw IllegalStateException("No active session")

    override fun startSession(ark: ByteArray) {
        endSession()
        _ark = ark
        _sessionStarts.tryEmit(Unit)
    }

    override fun endSession() {
        try {
            _ark?.fill(0)
        } catch (_: DestroyFailedException) {
            // Not all SecretKey implementations support destroy
        }
        _ark = null
    }
}
