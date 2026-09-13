package de.davis.keygo.core.security.domain.repository

@Deprecated("Use :core:biometrics instead")
interface BiometricAvailabilityRepository {

    fun availability(): Boolean
}