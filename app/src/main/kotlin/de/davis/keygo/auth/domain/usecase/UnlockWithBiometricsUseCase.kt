package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.factory.BiometricCipherFactory
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.asUnitResult
import de.davis.keygo.core.domain.newResultOnSuccess
import de.davis.keygo.core.domain.onSuccess
import javax.crypto.Cipher

class UnlockWithBiometricsUseCase(
    private val biometricCipherFactory: BiometricCipherFactory,
    private val wrappedKeyRepository: BiometricWrappedKeyRepository,
    private val session: Session
) {

    suspend operator fun invoke(cipher: Cipher): Result<Unit, CryptographyError> =
        wrappedKeyRepository.getBiometricWrappedKeyData()
            .asResult(CryptographyError.WrappedKeyNotFound)
            .newResultOnSuccess {
                biometricCipherFactory.unwrapDataKey(
                    cipher = cipher,
                    wrappedKey = it.wrappedKey,
                )
            }
            .onSuccess {
                session.startSession(it)
            }
            .asUnitResult()
}