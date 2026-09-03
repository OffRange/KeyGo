package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.StateFlow

interface Session {

    /** Observable lock state, for callers that have to react to a session ending rather than read it. */
    val isActive: StateFlow<Boolean>

    /**
     * Runs [block] with the live ARK, or returns `null` without running it when locked. Null is the
     * ordinary locked branch every caller handles.
     *
     * The ARK is wiped in place when a session ends, so the array is only valid inside [block] -
     * copy what has to outlive it. The bytes stay intact for the whole of [block] however long it
     * suspends, even if the session ends underneath.
     */
    suspend fun <R> withArk(block: suspend (ByteArray) -> R): R?

    fun startSession(ark: ByteArray)
    fun endSession()
}

/** [Session.withArk] for callers in [Result]: a locked session becomes [locked], not a null. */
suspend fun <R, E> Session.withArkOr(
    locked: E,
    block: suspend (ByteArray) -> Result<R, E>,
): Result<R, E> = withArk(block) ?: Result.Failure(locked)
