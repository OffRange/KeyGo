package de.davis.keygo.core.security.domain

class ArkHolder {

    private val lock = Any()

    private class Generation(val ark: ByteArray) {
        var readers = 0
        var wipe = false
    }

    private var current: Generation? = null

    suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? {
        val generation = synchronized(lock) {
            val gen = current ?: return null
            gen.readers++
            gen
        }

        try {
            return block(generation.ark)
        } finally {
            synchronized(lock) {
                generation.readers--
                if (generation.readers == 0 && generation.wipe) generation.ark.fill(0)
            }
        }
    }

    fun set(ark: ByteArray) = replace(ark)

    fun clear() = replace(null)

    private fun replace(next: ByteArray?) {
        synchronized(lock) {
            current?.let { retiring ->
                retiring.wipe = true
                if (retiring.readers == 0) retiring.ark.fill(0)
            }
            current = next?.let { Generation(it) }
        }
    }
}
