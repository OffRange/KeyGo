package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals

class BiometricCryptoErrorFromTest {

    @Test
    fun `a failed tag reports the key as invalidated`() {
        assertEquals(
            BiometricAuthError.KeyInvalidated,
            biometricCryptoErrorFrom(AEADBadTagException()),
        )
    }

    @Test
    fun `a failed tag is recognised through the wrapper the keystore throws`() {
        // What Cipher.unwrap actually raises: the tag failure arrives as a cause.
        val thrown = InvalidKeyException("Failed to unwrap key", AEADBadTagException())

        assertEquals(BiometricAuthError.KeyInvalidated, biometricCryptoErrorFrom(thrown))
    }

    @Test
    fun `an unrelated failure stays retryable`() {
        assertEquals(
            BiometricAuthError.CryptoFailed,
            biometricCryptoErrorFrom(IllegalStateException("keystore busy")),
        )
    }

    @Test
    fun `a self referencing cause chain terminates`() {
        val looping = object : RuntimeException("loops") {
            override val cause: Throwable get() = this
        }

        assertEquals(BiometricAuthError.CryptoFailed, biometricCryptoErrorFrom(looping))
    }
}
