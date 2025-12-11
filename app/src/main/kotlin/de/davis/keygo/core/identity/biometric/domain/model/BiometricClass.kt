package de.davis.keygo.core.identity.biometric.domain.model

@Deprecated("Migrate to :core:security")
sealed interface BiometricClass {
    data object Class2 : BiometricClass
    data object Class3 : BiometricClass
}