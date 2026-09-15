package de.davis.keygo.core.biometrics.domain.model


sealed interface BiometricAuthError {
    data object NoPromptHost : BiometricAuthError


    /** User canceled the prompt by pressing the negative button. */
    data object Declined : BiometricAuthError
    data object LockedOut : BiometricAuthError
    data object Canceled : BiometricAuthError

    /** Any other prompt error (timeout, vendor-specific, hardware). [errString] is user-facing. */
    data class Unknown(val errorCode: Int, val errString: String) : BiometricAuthError

    /** Biometrics cannot be used at all (no hardware, none enrolled, etc.). */
    data object BiometricsNotAvailable : BiometricAuthError
    data object NoCipher : BiometricAuthError

    data object CryptoFailed : BiometricAuthError
    data object KeyInvalidated : BiometricAuthError
}
