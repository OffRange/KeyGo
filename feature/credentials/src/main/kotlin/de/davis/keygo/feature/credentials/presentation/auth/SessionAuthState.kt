package de.davis.keygo.feature.credentials.presentation.auth

import androidx.biometric.BiometricPrompt
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.domain.model.BiometricAuthError

internal sealed interface SessionAuthState {
    data object TryBiometric : SessionAuthState
    data object NeedsPassword : SessionAuthState
    data object Authenticated : SessionAuthState
}

internal enum class UnlockOutcome { Abort, NeedsPassword }

internal fun mapUnlockError(error: UnlockError): UnlockOutcome = when (error) {
    is UnlockError.BiometricFailed -> when (val biometricError = error.error) {
        is BiometricAuthError.BiometricError -> when (biometricError.errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED -> UnlockOutcome.Abort

            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> UnlockOutcome.NeedsPassword

            else -> UnlockOutcome.NeedsPassword
        }

        is BiometricAuthError.CanNotAuthenticate -> UnlockOutcome.NeedsPassword
        BiometricAuthError.NoCipher -> UnlockOutcome.Abort
    }

    UnlockError.WrappedKeyNotFound -> UnlockOutcome.NeedsPassword
    UnlockError.UnwrappingFailed,
    UnlockError.DerivationFailed,
    UnlockError.ActiveAccountNotFound -> UnlockOutcome.Abort
}
