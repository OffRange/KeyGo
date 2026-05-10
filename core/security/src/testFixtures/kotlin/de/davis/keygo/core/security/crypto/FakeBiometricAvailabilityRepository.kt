package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository

class FakeBiometricAvailabilityRepository : BiometricAvailabilityRepository {

    var isAvailable: Boolean = false

    override fun availability(): Boolean = isAvailable
}
