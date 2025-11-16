package de.davis.keygo.core.security.domain.model


sealed interface BiometricAuthError {

    @ConsistentCopyVisibility
    data class BiometricError internal constructor(
        val errorCode: Int,
        val errString: String
    ) : BiometricAuthError

    data class CanNotAuthenticate(val code: Int) : BiometricAuthError

    data object NoCipher : BiometricAuthError
}