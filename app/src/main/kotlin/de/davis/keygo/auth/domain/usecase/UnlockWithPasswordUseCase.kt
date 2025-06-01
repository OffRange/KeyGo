package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.di.annotation.PasswordQualifier
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.repository.DeviceInfoRepository
import de.davis.keygo.auth.domain.repository.KeyDerivationRepository
import de.davis.keygo.auth.domain.repository.PasswordWrappedKeyRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.asUnitResult
import de.davis.keygo.core.domain.model.crypto.asAesKey
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.zip
import org.koin.core.annotation.Single

@Single
class UnlockWithPasswordUseCase(
    private val cipherFactory: CipherFactory,
    @PasswordQualifier
    private val wrappedKeyRepository: PasswordWrappedKeyRepository,
    private val keyDerivationRepository: KeyDerivationRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val session: Session
) {

    suspend operator fun invoke(password: String): Result<Unit, CryptographyError> =
        wrappedKeyRepository.getWrappedKeyData()
            .asResult(CryptographyError.WrappedKeyNotFound)
            .zip { wrappedKey ->
                keyDerivationRepository.deriveKey(
                    password = password.toByteArray(),
                    salt = wrappedKey.salt,
                    parallelism = deviceInfoRepository.getNumCors(),
                )
            }
            .zip { wrappedKey, derivedKey ->
                cipherFactory.prepareCipher(
                    mode = CryptographicMode.Unwrap,
                    kek = derivedKey.asAesKey(),
                    iv = wrappedKey.iv,
                )
            }
            .zip { wrappedKey, _, cipher ->
                cipherFactory.unwrapDataKey(
                    cipher = cipher,
                    wrappedKey = wrappedKey.wrappedKey,
                )
            }
            .onSuccess {
                session.startSession(it.success4)
            }
            .asUnitResult()
}
