package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import javax.security.auth.DestroyFailedException

@Single
internal class SessionImpl : Session {

    private var _ark: ByteArray? = null
    private val _isActive = MutableStateFlow(false)

    override val ark: ByteArray
        get() = _ark ?: throw IllegalStateException("No active session")

    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    override fun startSession(ark: ByteArray) {
        endSession()
        _ark = ark
        _isActive.value = true
    }

    override fun endSession() {
        try {
            _ark?.fill(0)
        } catch (_: DestroyFailedException) {
            // Not all SecretKey implementations support destroy
        }
        _ark = null
        _isActive.value = false
    }
}
