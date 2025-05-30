package de.davis.keygo.auth.domain.model

import javax.crypto.Cipher

sealed interface BiometricRequest {

    data object Class2 : BiometricRequest
    data class Class3(val cipher: Cipher) : BiometricRequest
}