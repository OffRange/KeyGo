package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.model.BiometricClass
import de.davis.keygo.auth.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository

class GetBiometricAvailabilityUseCase(
    private val biometricKekRepository: BiometricKekRepository,
    private val wrappedKeyRepository: BiometricWrappedKeyRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository
) {

    suspend operator fun invoke(biometricClass: BiometricClass = BiometricClass.Class3): BiometricAvailability {
        if (!biometricKekRepository.hasKek()) {
            return BiometricAvailability.Unavailable(BiometricAvailability.Reason.NoKek)
        }
        val wrappedKeyData = wrappedKeyRepository.getBiometricWrappedKeyData()
        if (wrappedKeyData?.isValid() != true) {
            return BiometricAvailability.Unavailable(BiometricAvailability.Reason.InvalidWrappedKey)
        }

        return biometricAvailabilityRepository.availability(biometricClass = biometricClass)
    }
}