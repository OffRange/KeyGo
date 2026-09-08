package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
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
}
