package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.WrappedKey
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCryptoController {

    suspend fun requestWrap(
        keyId: KeyId,
        key: Key,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<WrappedKey, BiometricAuthError>

    suspend fun requestUnwrap(
        keyId: KeyId,
        wrappedKey: WrappedKey,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Key, BiometricAuthError>
}