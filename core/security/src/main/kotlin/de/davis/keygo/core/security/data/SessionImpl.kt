package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import org.koin.core.annotation.Single
import javax.security.auth.DestroyFailedException

@Single
internal class SessionImpl : Session {

    private var _dek: AesKey? = null

    override val dek: AesKey
        get() = _dek ?: throw IllegalStateException("No active session")

    override fun startSession(dek: AesKey) {
        endSession()
        _dek = dek
    }

    override fun endSession() {
        try {
            _dek?.key?.destroy()
        } catch (_: DestroyFailedException) {
            // Not all SecretKey implementations support destroy
        }
        _dek = null
    }
}