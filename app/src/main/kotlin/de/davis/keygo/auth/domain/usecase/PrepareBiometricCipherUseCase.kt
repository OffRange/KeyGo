package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.getOrNull
import org.koin.core.annotation.Single
import javax.crypto.Cipher

@Single
class PrepareBiometricCipherUseCase(
    private val kekRepository: BiometricKekRepository,
    @BiometricQualifier
    private val biometricWrappedKeyRepository: BiometricWrappedKeyRepository,
    private val cipherFactory: CipherFactory
) {

    suspend operator fun invoke(mode: CryptographicMode): Result<Cipher, CryptographyError> {
        val key = kekRepository.getKek()
            .getOrNull() ?: kekRepository.createKek()

        val iv = if (mode == CryptographicMode.Unwrap) {
            biometricWrappedKeyRepository.getWrappedKeyData()?.iv ?: return Result.Failure(
                CryptographyError.WrappedKeyNotFound
            )
        } else null

        return cipherFactory.prepareCipher(
            mode = mode,
            kek = key,
            iv = iv
        )
    }
}