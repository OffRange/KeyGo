package de.davis.keygo.core.biometrics.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiometricEnrollmentErrorTest {

    @Test
    fun `declining the prompt is a dismissal`() {
        assertTrue(
            BiometricEnrollmentError.BiometricFailed(BiometricAuthError.Declined).isUserDismissal()
        )
    }

    @Test
    fun `canceling the prompt is a dismissal`() {
        assertTrue(
            BiometricEnrollmentError.BiometricFailed(BiometricAuthError.Canceled).isUserDismissal()
        )
    }

    @Test
    fun `a prompt that failed on its own is not a dismissal`() {
        listOf(
            BiometricAuthError.LockedOut,
            BiometricAuthError.NoPromptHost,
            BiometricAuthError.NoCipher,
            BiometricAuthError.CryptoFailed,
            BiometricAuthError.KeyInvalidated,
            BiometricAuthError.BiometricsNotAvailable,
            BiometricAuthError.Unknown(errorCode = 3, errString = "timed out"),
        ).forEach {
            assertFalse(BiometricEnrollmentError.BiometricFailed(it).isUserDismissal(), "$it")
        }
    }

    @Test
    fun `failures outside the prompt are not dismissals`() {
        listOf(
            BiometricEnrollmentError.NoActiveAccount,
            BiometricEnrollmentError.NoActiveSession,
            BiometricEnrollmentError.WrappingFailed,
            BiometricEnrollmentError.PersistenceFailed,
        ).forEach {
            assertFalse(it.isUserDismissal(), "$it")
        }
    }
}
