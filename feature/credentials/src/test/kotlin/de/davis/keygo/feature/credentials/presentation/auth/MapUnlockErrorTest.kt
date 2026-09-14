package de.davis.keygo.feature.credentials.presentation.auth

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.identity.domain.model.UnlockError
import kotlin.test.Test
import kotlin.test.assertEquals

class MapUnlockErrorTest {

    private fun biometric(error: BiometricAuthError) =
        mapUnlockError(UnlockError.BiometricFailed(error))

    @Test
    fun `the user backing out of the prompt gives the request back`() {
        assertEquals(UnlockOutcome.Abort, biometric(BiometricAuthError.Canceled))
    }

    @Test
    fun `a prompt that could not be shown gives the request back`() {
        assertEquals(UnlockOutcome.Abort, biometric(BiometricAuthError.NoPromptHost))
        assertEquals(UnlockOutcome.Abort, biometric(BiometricAuthError.NoCipher))
    }

    @Test
    fun `asking for the password offers the password form`() {
        assertEquals(UnlockOutcome.NeedsPassword, biometric(BiometricAuthError.Declined))
    }

    @Test
    fun `biometrics that cannot get the user in offer the password form`() {
        listOf(
            BiometricAuthError.LockedOut,
            BiometricAuthError.CryptoFailed,
            BiometricAuthError.KeyInvalidated,
            BiometricAuthError.BiometricsNotAvailable,
            BiometricAuthError.Unknown(errorCode = 3, errString = "timed out"),
        ).forEach {
            assertEquals(UnlockOutcome.NeedsPassword, biometric(it), "$it")
        }
    }

    @Test
    fun `an account the password can still open offers the password form`() {
        assertEquals(
            UnlockOutcome.NeedsPassword,
            mapUnlockError(UnlockError.BiometricEnrollmentReset),
        )
        assertEquals(UnlockOutcome.NeedsPassword, mapUnlockError(UnlockError.WrappedKeyNotFound))
    }

    @Test
    fun `failures a password cannot fix give the request back`() {
        listOf(
            UnlockError.UnwrappingFailed,
            UnlockError.DerivationFailed,
            UnlockError.ActiveAccountNotFound,
        ).forEach {
            assertEquals(UnlockOutcome.Abort, mapUnlockError(it), "$it")
        }
    }
}
