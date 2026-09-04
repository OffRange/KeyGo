package de.davis.keygo.core.security.domain

class ArkHolder {

    private val lock = Any()

    private var ark: ByteArray? = null
    private var readers = 0
    private val awaitingWipe = mutableListOf<ByteArray>()

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

    fun set(ark: ByteArray) = replace(ark)

    fun clear() = replace(null)

    private fun replace(next: ByteArray?) {
        synchronized(lock) {
            ark?.let { awaitingWipe += it }
            ark = next
            if (readers == 0) wipePending()
        }
    }

    private fun wipePending() {
        awaitingWipe.forEach { it.fill(0) }
        awaitingWipe.clear()
    }
}
