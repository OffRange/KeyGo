package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCryptoController {

    suspend fun requestWrap(
        keyId: KeyId,
        key: Key,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<CiphertextData, BiometricAuthError>

    suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Key, BiometricAuthError>

    suspend fun requestEncryption(
        keyId: KeyId,
        byteArray: ByteArray,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<CiphertextData, BiometricAuthError>

    suspend fun requestDecryption(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<ByteArray, BiometricAuthError>
}