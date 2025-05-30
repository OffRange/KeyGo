package de.davis.keygo.auth.domain.model

sealed interface BiometricClass {
    data object Class2 : BiometricClass
    data object Class3 : BiometricClass
}