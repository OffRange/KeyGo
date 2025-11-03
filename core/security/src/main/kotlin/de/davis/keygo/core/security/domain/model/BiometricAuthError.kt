package de.davis.keygo.core.security.domain.model


sealed interface BiometricAuthError {

    @ConsistentCopyVisibility
    data class BiometricError internal constructor(
        val errorCode: Int,
        val errString: String
    ) : BiometricAuthError

    data object NoHardware : BiometricAuthError

    data object NoCipher : BiometricAuthError
}