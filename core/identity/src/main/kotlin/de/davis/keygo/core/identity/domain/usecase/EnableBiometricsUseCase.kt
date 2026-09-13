package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.BiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.useArk
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import org.koin.core.annotation.Single

@Single
class EnableBiometricsUseCase(
    private val accountRepository: AccountRepository,
    private val session: Session,
    private val keyStoreManager: KeyStoreManager,
    private val biometricCrypto: BiometricCrypto,
) {

    suspend operator fun invoke(policy: BiometricPolicy = BiometricPolicy.Default) = resultBinding {
        val account = accountRepository.getOrNull()
            .asResult(BiometricEnrollmentError.NoActiveAccount)
            .bind()

        if (account.biometricWrappedArk == null) keyStoreManager.deleteKey(KeyId.BiometricVaultKek)

        val wrapped = session.useArk { ark ->
            biometricCrypto.requestWrap(
                keyId = KeyId.BiometricVaultKek,
                key = ark,
                policy = policy,
            ).bind { BiometricEnrollmentError.BiometricFailed(it) }
        }.bind { BiometricEnrollmentError.NoActiveSession }

        accountRepository.set(account.copy(biometricWrappedArk = wrapped.toBiometricWrappedArk()))
            .bind { BiometricEnrollmentError.PersistenceFailed }
    }
}
