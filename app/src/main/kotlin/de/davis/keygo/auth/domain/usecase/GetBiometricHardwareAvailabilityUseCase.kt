package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.model.BiometricClass
import de.davis.keygo.auth.domain.repository.BiometricAvailabilityRepository
import org.koin.core.annotation.Single

@Single
class GetBiometricHardwareAvailabilityUseCase(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository
) {

    operator fun invoke(biometricClass: BiometricClass = BiometricClass.Class3): BiometricAvailability =
        biometricAvailabilityRepository.availability(biometricClass = biometricClass)
}