package de.davis.keygo.core.identity.domain.model

sealed interface VerifyPasswordError {

    data object ActiveAccountNotFound : VerifyPasswordError
    data object IncorrectPassword : VerifyPasswordError
    data object KeyDerivationFailed : VerifyPasswordError
}
