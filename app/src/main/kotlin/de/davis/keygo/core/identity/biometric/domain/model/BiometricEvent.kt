package de.davis.keygo.core.identity.biometric.domain.model

import javax.crypto.Cipher

sealed interface BiometricEvent {
    data class OnAuthenticationSucceeded(
        val cipher: Cipher? = null
    ) : BiometricEvent

    data class OnAuthenticationError(val errorCode: Int, val errString: String) : BiometricEvent
}