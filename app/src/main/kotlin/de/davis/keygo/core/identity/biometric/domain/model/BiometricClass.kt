package de.davis.keygo.core.identity.biometric.domain.model

sealed interface BiometricClass {
    data object Class2 : BiometricClass
    data object Class3 : BiometricClass
}