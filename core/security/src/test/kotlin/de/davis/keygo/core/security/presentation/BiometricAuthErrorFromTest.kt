package de.davis.keygo.core.security.presentation

import androidx.biometric.BiometricPrompt
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import kotlin.test.Test
import kotlin.test.assertEquals

class BiometricAuthErrorFromTest {

    @Test
    fun `negative button maps to Declined`() {
        assertEquals(
            BiometricAuthError.Declined,
            biometricAuthErrorFrom(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "use password"),
        )
    }

    @Test
    fun `lockout maps to LockedOut`() {
        assertEquals(
            BiometricAuthError.LockedOut,
            biometricAuthErrorFrom(BiometricPrompt.ERROR_LOCKOUT, "too many attempts"),
        )
    }

    @Test
    fun `permanent lockout maps to LockedOut`() {
        assertEquals(
            BiometricAuthError.LockedOut,
            biometricAuthErrorFrom(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, "locked out"),
        )
    }

    @Test
    fun `user cancel maps to Canceled`() {
        assertEquals(
            BiometricAuthError.Canceled,
            biometricAuthErrorFrom(BiometricPrompt.ERROR_USER_CANCELED, "canceled"),
        )
    }

    @Test
    fun `system cancel maps to Canceled`() {
        assertEquals(
            BiometricAuthError.Canceled,
            biometricAuthErrorFrom(BiometricPrompt.ERROR_CANCELED, "canceled"),
        )
    }

    @Test
    fun `timeout maps to Unknown carrying code and message`() {
        assertEquals(
            BiometricAuthError.Unknown(BiometricPrompt.ERROR_TIMEOUT, "timed out"),
            biometricAuthErrorFrom(BiometricPrompt.ERROR_TIMEOUT, "timed out"),
        )
    }
}
