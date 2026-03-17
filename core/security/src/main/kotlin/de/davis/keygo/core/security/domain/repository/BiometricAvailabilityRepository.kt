package de.davis.keygo.core.security.domain.repository

interface BiometricAvailabilityRepository {

    fun availability(): Boolean
}