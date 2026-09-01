package de.davis.keygo.core.security.domain

import kotlinx.coroutines.flow.StateFlow

interface Session {

    /**
     * The ARK of the live session, or `null` when there is no active session.
     *
     * Nullable on purpose: a locked session is an ordinary branch every caller has to handle, not an
     * exceptional one. Reading it is the liveness check, so there is no separate guard to forget.
     */
    val ark: ByteArray?

    /** Observable lock state, for callers that have to react to a session ending rather than read it. */
    val isActive: StateFlow<Boolean>

    fun startSession(ark: ByteArray)
    fun endSession()
}
