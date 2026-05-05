package de.davis.keygo.core.identity.domain.model

sealed interface UnlockError {
    data object WrappedKeyNotFound : UnlockError
    data object UnwrappingFailed : UnlockError
    data object DerivationFailed : UnlockError
    data object ActiveAccountNotFound : UnlockError
}