package de.davis.keygo.core.biometrics.domain.repository

interface BiometricAvailabilityRepository {

    fun availability(): Boolean
}