package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.factory.BiometricCipherFactory
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.model.KeyStoreError
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.getOrNull
import de.davis.keygo.core.domain.onFailure
import javax.crypto.Cipher

class PrepareBiometricCipherUseCase(
    private val kekRepository: BiometricKekRepository,
    private val biometricWrappedKeyRepository: BiometricWrappedKeyRepository,
    private val biometricCipherFactory: BiometricCipherFactory
) {

    suspend operator fun invoke(mode: CryptographicMode): Result<Cipher, CryptographyError> {
        val key = kekRepository.getKek()
            .onFailure {
                return when (it) {
                    is KeyStoreError.KeyNotFound -> Result.Failure(CryptographyError.KeyNotInKeyStore)
                }
            }
            .getOrNull() ?: error("Unreachable")

        val wrappedKeyData = biometricWrappedKeyRepository.getBiometricWrappedKeyData()
            ?: return Result.Failure(CryptographyError.WrappedKeyNotFound)

        return biometricCipherFactory.prepareCipher(
            mode = mode,
            kek = key,
            iv = wrappedKeyData.iv
        )
    }
}