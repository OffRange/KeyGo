package de.davis.keygo.feature.credentials.presentation.activity

import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.KeyInfo

sealed interface CreatePasskeyBiometricRequestEvent {
    data class UnwrapPasskeyEncryptionKey(
        val wrappedKey: ByteArray,
        val policy: BiometricPolicy = BiometricPolicy.Default
    ) : CreatePasskeyBiometricRequestEvent {
        val keyInfo: KeyInfo = KeyInfo.PasskeyEncryptionKey


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UnwrapPasskeyEncryptionKey

            if (!wrappedKey.contentEquals(other.wrappedKey)) return false
            if (policy != other.policy) return false
            if (keyInfo != other.keyInfo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = wrappedKey.contentHashCode()
            result = 31 * result + policy.hashCode()
            result = 31 * result + keyInfo.hashCode()
            return result
        }
    }
}