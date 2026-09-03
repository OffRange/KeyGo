package de.davis.keygo.core.security.domain

/**
 * Holds a session's ARK and owns its wipe. A [withArk] block keeps intact bytes for its whole
 * duration: a session ending underneath defers the wipe to the last block out. Shared by the real
 * session and its fake so the rule cannot drift between them.
 */
class ArkHolder {

    /** Never held across [withArk]'s block - a monitor cannot span a suspension point. */
    private val lock = Any()

    private var ark: ByteArray? = null

    /** How many [withArk] blocks are running. A wipe waits for this to reach zero. */
    private var readers = 0

    /** ARKs dropped while a reader held them. Several pile up across repeated end and start. */
    private val awaitingWipe = mutableListOf<ByteArray>()

    /** Runs [block] with the held ARK, or returns `null` without running it when there is none. */
    suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? {
        val live = synchronized(lock) {
            val current = ark ?: return null
            readers++
            current
        }

        try {
            return block(live)
        } finally {
            synchronized(lock) {
                readers--
                if (readers == 0) wipePending()
            }
        }
    }

    /** Takes [ark] as the held one, dropping whatever it replaces. */
    fun set(ark: ByteArray) = replace(ark)

    /** Puts the ARK out of reach at once, wiping it unless a [withArk] block still holds it. */
    fun clear() = replace(null)

    private fun replace(next: ByteArray?) {
        synchronized(lock) {
            ark?.let { awaitingWipe += it }
            ark = next
            if (readers == 0) wipePending()
        }
    }

    /** Callers hold [lock]. */
    private fun wipePending() {
        awaitingWipe.forEach { it.fill(0) }
        awaitingWipe.clear()
    }
}
