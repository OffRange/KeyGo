package de.davis.keygo.core.security.domain.model


sealed interface BiometricAuthError {

    /** User chose the negative button (e.g. "Use password") instead of authenticating. */
    data object Declined : BiometricAuthError

    /** Too many failed attempts; biometrics are temporarily or permanently locked out. */
    data object LockedOut : BiometricAuthError

    /** User dismissed the prompt (back press, system cancel) without a decision. */
    data object Canceled : BiometricAuthError

    /** Any other prompt error (timeout, vendor-specific, hardware). [errString] is user-facing. */
    data class Unknown(val errorCode: Int, val errString: String) : BiometricAuthError

    /** Biometrics cannot be used at all (no hardware, none enrolled, etc.). */
    data class CanNotAuthenticate(val code: Int) : BiometricAuthError

    /** Authentication succeeded but no crypto object/cipher was returned. */
    data object NoCipher : BiometricAuthError
}
