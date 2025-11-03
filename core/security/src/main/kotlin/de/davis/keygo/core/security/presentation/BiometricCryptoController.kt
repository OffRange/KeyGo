package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.KeyInfo
import de.davis.keygo.core.util.Result
import java.security.Key

interface BiometricCryptoController {

    suspend fun requestWrap(
        keyInfo: KeyInfo,
        key: Key,
        policy: BiometricPolicy = BiometricPolicy.Companion.Default
    ): Result<ByteArray, BiometricAuthError>

    suspend fun requestUnwrap(
        keyInfo: KeyInfo,
        wrappedKey: ByteArray,
        policy: BiometricPolicy = BiometricPolicy.Companion.Default
    ): Result<Key, BiometricAuthError>
}