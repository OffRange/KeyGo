package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.BiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.resultBinding
import org.koin.core.annotation.Single

@Single
class UnlockWithBiometricsUseCase(
    private val session: Session,
    private val accountRepository: AccountRepository,
    private val biometricCrypto: BiometricCrypto,
    private val disableBiometrics: DisableBiometricsUseCase,
) {

    suspend operator fun invoke(
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Unit, UnlockError> = resultBinding {
        val account = accountRepository.getOrNull()
            .asResult(UnlockError.ActiveAccountNotFound)
            .bind()

        val wrappedKey = account.biometricWrappedArk
            .asResult(UnlockError.WrappedKeyNotFound)
            .bind()

        val key = biometricCrypto.requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            ciphertextData = CiphertextData(
                bytes = wrappedKey.key,
                iv = wrappedKey.keyIV
            ),
            policy = policy,
        ).bind { error ->
            when (error) {
                BiometricAuthError.KeyInvalidated -> {
                    disableBiometrics().fold(
                        onSuccess = { UnlockError.BiometricEnrollmentReset },
                        onFailure = { UnlockError.BiometricFailed(BiometricAuthError.KeyInvalidated) }
                    )
                }

                else -> UnlockError.BiometricFailed(error)
            }
        }

        val ark = key.encoded
        try {
            session.unlockWithArk(ark).mapFailure { UnlockError.UnwrappingFailed }
        } finally {
            ark.fill(0)
        }
    }
}