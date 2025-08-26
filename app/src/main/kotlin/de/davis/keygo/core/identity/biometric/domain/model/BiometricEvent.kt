package de.davis.keygo.core.identity.biometric.domain.model

import de.davis.keygo.core.identity.biometric.presentation.model.BiometricRequest
import javax.crypto.Cipher

sealed interface BiometricEvent {
    data class OnAuthenticationSucceeded(
        val requestReason: BiometricRequest.Reason,
        val cipher: Cipher? = null
    ) : BiometricEvent

    data class OnAuthenticationError(val errorCode: Int, val errString: String) : BiometricEvent
}