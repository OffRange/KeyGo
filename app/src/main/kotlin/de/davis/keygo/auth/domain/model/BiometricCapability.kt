package de.davis.keygo.auth.domain.model

sealed interface BiometricCapability {

    data object Available : BiometricCapability
    data class Unavailable(val reason: Reason) : BiometricCapability

    enum class Reason {
        NoHardware,
        HardwareUnavailable,
        NoneEnrolled,
        SecurityUpdateRequired,
        Unsupported,
        Unknown
    }
}