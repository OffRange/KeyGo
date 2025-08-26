package de.davis.keygo.core.identity.biometric.domain.usecase

import de.davis.keygo.core.di.annotation.BiometricQualifier
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.getOrNull
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricKekRepository
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
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
        val iv = if (mode == CryptographicMode.Unwrap) {
            biometricWrappedKeyRepository.getWrappedKeyData()?.iv ?: return Result.Failure(
                CryptographyError.WrappedKeyNotFound
            )
        } else null

        val key = kekRepository.getKek().getOrNull()
            ?: kekRepository.createKek()

        return cipherFactory.prepareCipher(
            mode = mode,
            kek = key,
            iv = iv
        )
    }
}