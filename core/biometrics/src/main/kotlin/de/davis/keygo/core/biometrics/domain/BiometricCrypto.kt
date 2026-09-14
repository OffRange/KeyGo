package de.davis.keygo.core.biometrics.domain

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCrypto {

    suspend fun requestWrap(
        keyId: KeyId,
        key: ByteArray,
    ): Result<CryptographicData, BiometricAuthError>
        policy: BiometricPolicy = BiometricPolicy.Default,

    suspend fun requestUnwrap(
        keyId: KeyId,
        cryptographicData: CryptographicData,
        policy: BiometricPolicy = BiometricPolicy.Default,
    ): Result<Key, BiometricAuthError>
}
