package de.davis.keygo.core.biometrics.domain

import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key
import javax.crypto.Cipher

interface BiometricCrypto {

    suspend fun requestCipher(
        keyId: KeyId,
        mode: CryptographicMode,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Cipher, BiometricAuthError>

    suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Key, BiometricAuthError>
}