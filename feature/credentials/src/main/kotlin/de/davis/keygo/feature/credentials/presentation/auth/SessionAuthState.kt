package de.davis.keygo.feature.credentials.presentation.auth

import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.domain.model.BiometricAuthError

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

        BiometricAuthError.Declined,
        BiometricAuthError.LockedOut,
        is BiometricAuthError.Unknown,
        is BiometricAuthError.CanNotAuthenticate -> UnlockOutcome.NeedsPassword
    }

    UnlockError.WrappedKeyNotFound -> UnlockOutcome.NeedsPassword
    UnlockError.UnwrappingFailed,
    UnlockError.DerivationFailed,
    UnlockError.ActiveAccountNotFound -> UnlockOutcome.Abort
}
