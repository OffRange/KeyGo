package de.davis.keygo.feature.credentials.presentation.activity

import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.WrappedKey

sealed interface CreatePasskeyBiometricRequestEvent {
    data class UnwrapPasskeyEncryptionKey(
        val wrappedKey: WrappedKey,
        val policy: BiometricPolicy = BiometricPolicy.Default
    ) : CreatePasskeyBiometricRequestEvent {
        val keyId: KeyId = KeyId.PasskeyEncryptionKey
    }
}