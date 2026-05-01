package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import javax.crypto.spec.SecretKeySpec

/**
 * A fake implementation of [Session] that provides a fixed DEK for testing purposes.
 */
class FakeSession : Session {

    override val dek: AesKey
        get() = AesKey(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))

    override fun startSession(dek: AesKey) = Unit
    override fun endSession() = Unit
}