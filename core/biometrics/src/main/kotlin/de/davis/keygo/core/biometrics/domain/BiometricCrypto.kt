package de.davis.keygo.core.biometrics.domain

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCrypto {

    suspend fun <T> requestWrap(
        keyId: KeyId,
        policy: BiometricPolicy = BiometricPolicy.Default,
        wrap: (seal: (key: ByteArray) -> CryptographicData) -> T,
    ): Result<T, BiometricAuthError>

    suspend fun requestUnwrap(
        keyId: KeyId,
        cryptographicData: CryptographicData,
        policy: BiometricPolicy = BiometricPolicy.Default,
    ): Result<Key, BiometricAuthError>
}
