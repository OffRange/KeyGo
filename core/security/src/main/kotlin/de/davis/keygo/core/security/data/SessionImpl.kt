package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import org.koin.core.annotation.Single

@Single
internal class SessionImpl : Session {

    override fun startSession(dek: AesKey) {
        endSession()
        scope.declare(dek)
    }

    override fun endSession() {
        if (scope.isNotClosed()) {
            scope.close()
        }
    }
}