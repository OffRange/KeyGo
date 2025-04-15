package de.davis.keygo.auth.domain.model

sealed interface BiometricRequest {

    data object Class2 : BiometricRequest
    // TODO data object Class3 : BiometricRequest
}