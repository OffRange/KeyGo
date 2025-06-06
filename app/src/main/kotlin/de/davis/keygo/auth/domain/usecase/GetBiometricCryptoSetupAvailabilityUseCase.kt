package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import org.koin.core.annotation.Single

@Single
class GetBiometricCryptoSetupAvailabilityUseCase(
    private val biometricKekRepository: BiometricKekRepository,
    @BiometricQualifier
    private val wrappedKeyRepository: BiometricWrappedKeyRepository,
) {

    suspend operator fun invoke(): BiometricAvailability {
        if (!biometricKekRepository.hasKek()) {
            return BiometricAvailability.Unavailable(BiometricAvailability.Reason.NoKek)
        }
        val wrappedKeyData = wrappedKeyRepository.getWrappedKeyData()
        if (wrappedKeyData?.isValid() != true) {
            return BiometricAvailability.Unavailable(BiometricAvailability.Reason.InvalidWrappedKey)
        }

        return BiometricAvailability.Available
    }
}