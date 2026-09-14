package de.davis.keygo.feature.credentials.presentation.auth

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.identity.domain.model.UnlockError

internal sealed interface SessionAuthState {
    data object TryBiometric : SessionAuthState
    data object NeedsPassword : SessionAuthState
    data object Authenticated : SessionAuthState
}

internal enum class UnlockOutcome { Abort, NeedsPassword }

internal fun mapUnlockError(error: UnlockError): UnlockOutcome = when (error) {
    is UnlockError.BiometricFailed -> when (error.error) {
        BiometricAuthError.Canceled,
        BiometricAuthError.NoCipher -> UnlockOutcome.Abort

        BiometricAuthError.NoPromptHost,
        BiometricAuthError.Declined,
        BiometricAuthError.LockedOut,
        BiometricAuthError.CryptoFailed,
        BiometricAuthError.KeyInvalidated,
        BiometricAuthError.BiometricsNotAvailable,
        is BiometricAuthError.Unknown,
            -> UnlockOutcome.NeedsPassword
    }

    UnlockError.BiometricEnrollmentReset,
    UnlockError.WrappedKeyNotFound -> UnlockOutcome.NeedsPassword

    UnlockError.UnwrappingFailed,
    UnlockError.DerivationFailed,
    UnlockError.ActiveAccountNotFound -> UnlockOutcome.Abort
}
