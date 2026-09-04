package de.davis.keygo.core.security.domain

interface SystemHandoff {

    val isPending: Boolean

    fun expectReturn()
    fun returned()
    fun clear()
}

inline fun SystemHandoff.forRoundTrip(open: () -> Unit) {
    expectReturn()
    try {
        open()
    } catch (e: Throwable) {
        returned()
        throw e
    }
}
