package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.identity.domain.model.UnlockableByBiometricsResult
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import org.koin.core.annotation.Single

@Single
class UnlockableByBiometricsUseCase(
    private val accountRepository: AccountRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
) {

    suspend operator fun invoke(): UnlockableByBiometricsResult {
        val hardwareAvailable = biometricAvailabilityRepository.availability()

        val account = accountRepository.getOrNull()
            ?: return UnlockableByBiometricsResult.NoAccount(hardwareAvailable)
        if (!hardwareAvailable) return UnlockableByBiometricsResult.NoHardware
        if (account.biometricWrappedArk == null) return UnlockableByBiometricsResult.NotEnrolled

        return UnlockableByBiometricsResult.Available
    }
}