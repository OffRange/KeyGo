package de.davis.keygo.core.security.domain

interface Session {

    val ark: ByteArray

    fun startSession(ark: ByteArray)
    fun endSession()
}