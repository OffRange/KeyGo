package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import org.koin.core.annotation.Single

@Single
class DisableBiometricsUseCase(
    private val accountRepository: AccountRepository,
    private val keyStoreManager: KeyStoreManager,
) {

    suspend operator fun invoke() = resultBinding {
        val account = accountRepository.getOrNull()
            .asResult(BiometricEnrollmentError.NoActiveAccount)
            .bind()

        accountRepository.set(account.copy(biometricWrappedArk = null))
            .bind { BiometricEnrollmentError.PersistenceFailed }

        keyStoreManager.deleteKey(KeyId.BiometricVaultKek)
    }
}
