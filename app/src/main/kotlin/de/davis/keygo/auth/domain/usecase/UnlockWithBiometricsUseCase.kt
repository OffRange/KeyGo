package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.asUnitResult
import de.davis.keygo.core.domain.newResultOnSuccess
import de.davis.keygo.core.domain.onSuccess
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
            .newResultOnSuccess {
                cipherFactory.unwrapDataKey(
                    cipher = cipher,
                    wrappedKey = it.wrappedKey,
                )
            }
            .onSuccess {
                session.startSession(it)
            }
            .asUnitResult()
}