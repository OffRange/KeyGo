package de.davis.keygo.core.data

import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.model.crypto.AesKey
import org.koin.core.annotation.Single

@Single
class SessionImpl : Session {

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