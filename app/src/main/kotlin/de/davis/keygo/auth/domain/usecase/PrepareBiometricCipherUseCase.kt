package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.model.KeyStoreError
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.getOrNull
import de.davis.keygo.core.domain.onFailure
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
            .onFailure {
                return when (it) {
                    is KeyStoreError.KeyNotFound -> Result.Failure(CryptographyError.KeyNotInKeyStore)
                }
            }
            .getOrNull() ?: error("Unreachable")

        val wrappedKeyData = biometricWrappedKeyRepository.getWrappedKeyData()
            ?: return Result.Failure(CryptographyError.WrappedKeyNotFound)

        return cipherFactory.prepareCipher(
            mode = mode,
            kek = key,
            iv = wrappedKeyData.iv
        )
    }
}