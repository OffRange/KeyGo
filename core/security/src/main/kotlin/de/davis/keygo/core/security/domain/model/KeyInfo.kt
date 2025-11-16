package de.davis.keygo.core.security.domain.model

data class KeyInfo(val id: String) {

    companion object {
        val PasskeyEncryptionKey = KeyInfo("passkey_encryption_key")
    }
}