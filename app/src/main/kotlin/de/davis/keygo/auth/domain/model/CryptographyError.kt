package de.davis.keygo.auth.domain.model

sealed interface CryptographyError {
    data object IllegalState : CryptographyError
    data object NoSuchAlgorithm : CryptographyError
    data object InvalidKey : CryptographyError

    data object KeyNotInKeyStore : CryptographyError
    data object WrappedKeyNotFound : CryptographyError
    data class Unknown(val throwable: Throwable) : CryptographyError
}