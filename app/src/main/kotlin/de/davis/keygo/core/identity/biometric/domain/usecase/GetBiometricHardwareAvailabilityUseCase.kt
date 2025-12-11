package de.davis.keygo.core.identity.biometric.domain.usecase

import de.davis.keygo.core.identity.biometric.domain.model.BiometricAvailability
import de.davis.keygo.core.identity.biometric.domain.model.BiometricClass
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricAvailabilityRepository
import org.koin.core.annotation.Single

@Single
@Deprecated("Migrate to :core:security")
class GetBiometricHardwareAvailabilityUseCase(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository
) {

    operator fun invoke(biometricClass: BiometricClass = BiometricClass.Class3): BiometricAvailability =
        biometricAvailabilityRepository.availability(biometricClass = biometricClass)
}