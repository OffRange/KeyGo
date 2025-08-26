package de.davis.keygo.core.identity.biometric.domain.repository

import de.davis.keygo.core.identity.biometric.domain.model.BiometricAvailability
import de.davis.keygo.core.identity.biometric.domain.model.BiometricClass

interface BiometricAvailabilityRepository {

    fun availability(biometricClass: BiometricClass): BiometricAvailability
}