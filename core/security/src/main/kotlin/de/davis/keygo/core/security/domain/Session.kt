package de.davis.keygo.core.security.domain

import kotlinx.coroutines.flow.Flow

interface Session {

    val ark: ByteArray

    /**
     * Emits once per [startSession], for work that has to happen on every unlock however the user
     * got there.
     *
     * A session is started from the auth screen, from the autofill service and from both passkey
     * activities. A caller that enumerated those instead would go stale the next time a fifth way
     * in is added, and would do so silently.
     */
    val sessionStarts: Flow<Unit>

    fun startSession(ark: ByteArray)
    fun endSession()
}
