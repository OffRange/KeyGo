package de.davis.keygo.core.biometrics

import de.davis.keygo.core.biometrics.domain.repository.BiometricAvailabilityRepository

class FakeBiometricAvailabilityRepository : BiometricAvailabilityRepository {

    var isAvailable: Boolean = false

    override fun availability(): Boolean = isAvailable
}
