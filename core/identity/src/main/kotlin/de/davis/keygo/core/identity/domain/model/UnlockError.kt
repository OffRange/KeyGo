package de.davis.keygo.core.identity.domain.model

import de.davis.keygo.core.security.domain.model.BiometricAuthError

sealed interface UnlockError {
    data object WrappedKeyNotFound : UnlockError
    data object UnwrappingFailed : UnlockError
    data object DerivationFailed : UnlockError
    data object ActiveAccountNotFound : UnlockError
    data class BiometricFailed(val error: BiometricAuthError) : UnlockError
}