package de.davis.keygo.auth.domain.model

sealed interface BiometricResult {

    data object Success : BiometricResult
    data object Failed : BiometricResult
    data object Error : BiometricResult
}