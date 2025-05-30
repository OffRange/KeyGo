package de.davis.keygo.auth.domain.repository

import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.model.BiometricClass

interface BiometricAvailabilityRepository {

    fun availability(biometricClass: BiometricClass): BiometricAvailability
}