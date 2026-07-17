package de.davis.keygo.core.security.domain.model

data class KeyId(
    val id: String,
    val needsAuthentication: Boolean,
) {

    companion object {
        val BiometricVaultKek = KeyId("biometric_vault_kek", true)
        val BackupPassphraseKey = KeyId("backup_passphrase_key", false)
        val BackupArkKey = KeyId("backup_ark_key", false)
    }
}