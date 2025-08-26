package de.davis.keygo.core.identity.biometric.domain.usecase

import de.davis.keygo.core.di.annotation.BiometricQualifier
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.asUnitResult
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.zip
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
import org.koin.core.annotation.Single
import javax.crypto.Cipher

@Single
class UnlockWithBiometricsUseCase(
    private val cipherFactory: CipherFactory,
    @BiometricQualifier
    private val wrappedKeyRepository: BiometricWrappedKeyRepository,
    private val session: Session
) {

    suspend operator fun invoke(cipher: Cipher): Result<Unit, CryptographyError> =
        wrappedKeyRepository.getWrappedKeyData()
            .asResult(CryptographyError.WrappedKeyNotFound)
            .zip {
                cipherFactory.unwrapDataKey(
                    cipher = cipher,
                    wrappedKey = it.wrappedKey,
                )
            }
            .onSuccess { (_, key) ->
                session.startSession(key)
            }
            .asUnitResult()
}