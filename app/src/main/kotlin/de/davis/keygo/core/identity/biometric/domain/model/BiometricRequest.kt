package de.davis.keygo.core.identity.biometric.domain.model

import javax.crypto.Cipher

sealed interface BiometricRequest {

    val title: String
    val negativeButtonText: String

    data class Class3(
        override val title: String,
        override val negativeButtonText: String,
        val cipher: Cipher
    ) : BiometricRequest
}