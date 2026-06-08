package de.davis.keygo.core.identity.domain.model

sealed interface ChangePasswordError {

    data object ActiveAccountNotFound : ChangePasswordError

    /** The supplied current password did not unwrap the stored ARK. */
    data object IncorrectPassword : ChangePasswordError

    /** Biometric proof supplied but the account has no biometric-wrapped ARK enrolled. */
    data object BiometricNotEnrolled : ChangePasswordError

    data object KeyDerivationFailed : ChangePasswordError

    data object WrappingFailed : ChangePasswordError

    data object PersistenceFailed : ChangePasswordError
}
