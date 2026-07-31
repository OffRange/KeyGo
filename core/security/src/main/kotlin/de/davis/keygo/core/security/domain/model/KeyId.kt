package de.davis.keygo.core.security.domain.model

/**
 * Every Android Keystore alias this app owns.
 *
 * An enum rather than a constructible type: [id] and [needsAuthentication] are only meaningful as a
 * pair, and [needsAuthentication] is read once, at key creation. A caller free to name an existing
 * alias under a weaker policy could otherwise mint an auth-free key where an auth-bound one was
 * intended, so the set of aliases is closed here by construction.
 */
enum class KeyId(val id: String, val needsAuthentication: Boolean) {
    /** Wraps the escrowed ARK; this key can only be used once unlocked via biometrics */
    BiometricVaultKek("biometric_vault_kek", true),

    /** Wraps the escrowed backup passphrase; auth-free so a scheduled backup can run unattended. */
    BackupPassphraseKey("backup_passphrase_key", false),

    /**
     * Wraps the escrowed ARK copy for decrypting data for backups; auth-free for the same reason
     * as [BackupPassphraseKey].
     */
    BackupArkKey("backup_ark_key", false),
}
