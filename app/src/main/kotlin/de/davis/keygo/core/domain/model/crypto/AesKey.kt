package de.davis.keygo.core.domain.model.crypto

import java.security.Key
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@JvmInline
value class AesKey(val key: SecretKey)

fun SecretKey.asAesKey() = AesKey(this)
fun Key.asAesKey() = AesKey(this as SecretKey)
fun ByteArray.asAesKey(): AesKey = AesKey(SecretKeySpec(this, 0, size, "AES"))