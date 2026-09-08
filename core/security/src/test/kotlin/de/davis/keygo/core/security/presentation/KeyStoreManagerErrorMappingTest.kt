package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The keystore has already classified the failure by the time the prompt sees it, so this mapping
 * only translates vocabulary: whatever is not a permanently invalidated key stays retryable.
 */
class KeyStoreManagerErrorMappingTest {

    @Test
    fun `an invalidated key stays invalidated in the prompt's vocabulary`() {
        assertEquals(
            BiometricAuthError.KeyInvalidated,
            KeyStoreManagerError.KeyInvalidated.toBiometricAuthError(),
        )
    }

    @Test
    fun `a key waiting on authentication is worth retrying`() {
        assertEquals(
            BiometricAuthError.CryptoFailed,
            KeyStoreManagerError.AuthenticationRequired.toBiometricAuthError(),
        )
    }

    @Test
    fun `an unnamed failure stays retryable`() {
        assertEquals(
            BiometricAuthError.CryptoFailed,
            KeyStoreManagerError.Unknown.toBiometricAuthError(),
        )
    }

    /**
     * The other half of the same vocabulary, for failures the cipher raises after the prompt has
     * already succeeded. A key whose ciphertext no longer verifies passes `cipher.init` and only
     * fails at unwrap, so this is the only place that particular death is visible - reporting it
     * as retryable is what would leave the user tapping an unlock that can never work.
     */
    @Test
    fun `an unwrap that fails its tag check reports the key as invalidated`() {
        val thrown = InvalidKeyException("Failed to unwrap key", AEADBadTagException())

        assertEquals(
            BiometricAuthError.KeyInvalidated,
            cipherFailureToBiometricAuthError(thrown),
        )
    }

    @Test
    fun `a bare tag failure reports the key as invalidated`() {
        assertEquals(
            BiometricAuthError.KeyInvalidated,
            cipherFailureToBiometricAuthError(AEADBadTagException()),
        )
    }

    @Test
    fun `a cipher failure that names nothing stays retryable`() {
        assertEquals(
            BiometricAuthError.CryptoFailed,
            cipherFailureToBiometricAuthError(IllegalBlockSizeException("short block")),
        )
    }
}
