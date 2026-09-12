package de.davis.keygo.core.biometrics.domain

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCrypto {

    suspend fun requestWrap(
        keyId: KeyId,
        key: ByteArray,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<CiphertextData, BiometricAuthError>

    suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Key, BiometricAuthError>
}