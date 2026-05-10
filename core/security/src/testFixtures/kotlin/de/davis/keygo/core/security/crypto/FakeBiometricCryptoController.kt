package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result
import java.security.Key
import javax.crypto.Cipher

class FakeBiometricCryptoController : BiometricCryptoController {

    var unwrapResult: Result<Key, BiometricAuthError> = Result.Failure(BiometricAuthError.NoCipher)

    override suspend fun requestCipher(
        keyId: KeyId,
        mode: CryptographicMode,
        policy: BiometricPolicy,
    ): Result<Cipher, BiometricAuthError> = Result.Failure(BiometricAuthError.NoCipher)

    override suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy,
    ): Result<Key, BiometricAuthError> = unwrapResult

    override suspend fun requestEncryption(
        keyId: KeyId,
        byteArray: ByteArray,
        policy: BiometricPolicy,
    ): Result<CiphertextData, BiometricAuthError> = Result.Failure(BiometricAuthError.NoCipher)

    override suspend fun requestDecryption(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy,
    ): Result<ByteArray, BiometricAuthError> = Result.Failure(BiometricAuthError.NoCipher)
}
