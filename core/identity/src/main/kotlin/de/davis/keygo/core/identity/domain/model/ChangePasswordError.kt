package de.davis.keygo.core.identity.domain.model

sealed interface ChangePasswordError {

    data object ActiveAccountNotFound : ChangePasswordError
    data object IncorrectPassword : ChangePasswordError
    data object BiometricNotEnrolled : ChangePasswordError
    data object KeyDerivationFailed : ChangePasswordError
    data object WrappingFailed : ChangePasswordError
    data object PersistenceFailed : ChangePasswordError
}
