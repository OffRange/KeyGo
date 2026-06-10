package de.davis.keygo.core.security.domain.model


sealed interface BiometricAuthError {
    data object Declined : BiometricAuthError
    data object LockedOut : BiometricAuthError
    data object Canceled : BiometricAuthError

    /** Any other prompt error (timeout, vendor-specific, hardware). [errString] is user-facing. */
    data class Unknown(val errorCode: Int, val errString: String) : BiometricAuthError

    /** Biometrics cannot be used at all (no hardware, none enrolled, etc.). */
    data class CanNotAuthenticate(val code: Int) : BiometricAuthError
    data object NoCipher : BiometricAuthError
}
