package de.davis.keygo.auth.domain.repository

import de.davis.keygo.auth.domain.model.BiometricCapability
import de.davis.keygo.auth.domain.model.BiometricRequest

interface CheckBiometricCapabilityRepository {

    fun getCapability(request: BiometricRequest = BiometricRequest.Class2): BiometricCapability
}