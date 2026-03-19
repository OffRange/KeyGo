package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.crypto.model.AesKey

interface Session {

    val dek: AesKey

    fun startSession(dek: AesKey)
    fun endSession()
}