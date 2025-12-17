package de.davis.keygo.feature.credentials.presentation.provide.activity

import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId

data class DecryptPasskeyEncryptionKeyRequest(
    val ciphertextData: CiphertextData,
    val policy: BiometricPolicy = BiometricPolicy.Default
) {
    val keyId: KeyId = KeyId.PasskeyEncryptionKey
}