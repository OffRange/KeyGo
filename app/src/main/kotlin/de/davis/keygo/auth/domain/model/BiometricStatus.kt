package de.davis.keygo.auth.domain.model

sealed interface BiometricStatus {

    data object Available : BiometricStatus
    data object Unavailable : BiometricStatus
}