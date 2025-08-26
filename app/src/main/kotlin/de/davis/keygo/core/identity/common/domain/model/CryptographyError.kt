package de.davis.keygo.core.identity.common.domain.model

sealed interface CryptographyError {
    data object IllegalState : CryptographyError
    data object NoSuchAlgorithm : CryptographyError
    data object InvalidKey : CryptographyError

    data object KeyNotInKeyStore : CryptographyError
    data object WrappedKeyNotFound : CryptographyError

    data class DerivationFailed(val reason: String) : CryptographyError
    data class Unknown(val throwable: Throwable) : CryptographyError
}