package de.davis.keygo.core.security.domain

import kotlinx.coroutines.flow.StateFlow

interface Session {

    val ark: ByteArray

    /** `.value` is a synchronous read, so this doubles as the guard check before touching [ark]. */
    val isActive: StateFlow<Boolean>

    fun startSession(ark: ByteArray)
    fun endSession()
}
