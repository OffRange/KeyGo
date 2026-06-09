package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.biometric.BiometricPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

class BiometricFallbackTest {

    @Test
    fun `negative button routes to UsePassword`() {
        assertEquals(
            BiometricFallback.UsePassword,
            biometricFallbackFor(BiometricPrompt.ERROR_NEGATIVE_BUTTON),
        )
    }

    @Test
    fun `lockout routes to UsePassword`() {
        assertEquals(BiometricFallback.UsePassword, biometricFallbackFor(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(
            BiometricFallback.UsePassword,
            biometricFallbackFor(BiometricPrompt.ERROR_LOCKOUT_PERMANENT),
        )
    }

    @Test
    fun `transient cancellation routes to Cancelled`() {
        assertEquals(BiometricFallback.Cancelled, biometricFallbackFor(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(BiometricFallback.Cancelled, biometricFallbackFor(BiometricPrompt.ERROR_TIMEOUT))
    }
}
