package de.davis.keygo.core.domain.model.crypto

import javax.crypto.SecretKey

@JvmInline
value class AesKey(val key: SecretKey)

fun SecretKey.asAesKey() = AesKey(this)