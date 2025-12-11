package de.davis.keygo.core.identity.biometric.domain.usecase

import de.davis.keygo.core.di.annotation.BiometricQualifier
import de.davis.keygo.core.identity.biometric.domain.model.BiometricAvailability
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricKekRepository
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
import org.koin.core.annotation.Single

@Single
@Deprecated("Migrate to :core:security")
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