package de.davis.keygo.auth.domain.model

sealed interface BiometricRequest {

    data object Class2 : BiometricRequest
    data object Class3 : BiometricRequest
}