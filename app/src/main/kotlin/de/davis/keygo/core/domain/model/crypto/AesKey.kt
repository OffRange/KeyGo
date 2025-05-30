package de.davis.keygo.core.domain.model.crypto

import java.security.Key
import javax.crypto.SecretKey

@JvmInline
value class AesKey(val key: SecretKey) : AutoCloseable {
    override fun close() {
        key.destroy()
    }
}

fun SecretKey.asAesKey() = AesKey(this)
fun Key.asAesKey() = AesKey(this as SecretKey)