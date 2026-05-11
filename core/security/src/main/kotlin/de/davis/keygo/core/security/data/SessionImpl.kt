package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.Session
import org.koin.core.annotation.Single
import javax.security.auth.DestroyFailedException

@Single
internal class SessionImpl : Session {

    private var _ark: ByteArray? = null

    override val ark: ByteArray
        get() = _ark ?: throw IllegalStateException("No active session")

    override fun startSession(ark: ByteArray) {
        endSession()
        _ark = ark
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