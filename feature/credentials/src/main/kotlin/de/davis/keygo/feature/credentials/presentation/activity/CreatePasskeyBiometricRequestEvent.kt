package de.davis.keygo.feature.credentials.presentation.activity

import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId

internal sealed interface CreatePasskeyBiometricRequestEvent {
    val keyId: KeyId
    val policy: BiometricPolicy

    data class DecryptPasskeyEncryptionKey(
        val ciphertextData: CiphertextData,
        override val policy: BiometricPolicy = BiometricPolicy.Default
    ) : CreatePasskeyBiometricRequestEvent {
        override val keyId: KeyId = KeyId.PasskeyEncryptionKey
    }

    data class EncryptPasskeyEncryptionKey(
        val key: ByteArray,
        override val policy: BiometricPolicy = BiometricPolicy.Default
    ) : CreatePasskeyBiometricRequestEvent {
        override val keyId: KeyId = KeyId.PasskeyEncryptionKey

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptPasskeyEncryptionKey

            if (!key.contentEquals(other.key)) return false
            if (policy != other.policy) return false
            if (keyId != other.keyId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + policy.hashCode()
            result = 31 * result + keyId.hashCode()
            return result
        }
    }
}