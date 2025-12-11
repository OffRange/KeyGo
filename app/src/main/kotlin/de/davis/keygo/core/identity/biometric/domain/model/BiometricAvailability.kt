package de.davis.keygo.core.identity.biometric.domain.model

@Deprecated("Migrate to :core:security")
sealed interface BiometricAvailability {

    data object Available : BiometricAvailability
    data class Unavailable(val reason: Reason) : BiometricAvailability

    enum class Reason {
        NoKek,
        InvalidWrappedKey,
        NoHardware,
        HardwareUnavailable,
        NoneEnrolled,
        SecurityUpdateRequired,
        Unsupported,
        Unknown
    }
}