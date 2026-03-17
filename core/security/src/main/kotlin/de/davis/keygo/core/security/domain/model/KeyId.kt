package de.davis.keygo.core.security.domain.model

data class KeyId(val id: String) {

    companion object {
        val PasskeyEncryptionKey = KeyId("passkey_encryption_key")
        val BiometricVaultKek = KeyId("biometric_vault_kek")
    }
}