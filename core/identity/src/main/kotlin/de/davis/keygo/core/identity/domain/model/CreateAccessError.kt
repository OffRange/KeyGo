package de.davis.keygo.core.identity.domain.model

sealed interface CreateAccessError {
    data object KeyDerivationFailed : CreateAccessError
    data object WrappingFailed : CreateAccessError
    data object AccountPersistenceFailed : CreateAccessError
    data class VaultPersistenceFailed(val cause: Throwable) : CreateAccessError
}