package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result

interface SystemHandoff {

    val isPending: Boolean

    fun expectReturn()
    fun returned()
    fun clear()
}

/**
 * [open] failing to launch the system screen (no activity resolves the intent, say) is expected
 * and foreseeable, not exceptional, so it is reported as a [Result.Failure] rather than thrown.
 */
inline fun SystemHandoff.forRoundTrip(open: () -> Unit): Result<Unit, Throwable> {
    expectReturn()
    return try {
        open()
        Result.Success(Unit)
    } catch (e: Throwable) {
        returned()
        Result.Failure(e)
    }
}
