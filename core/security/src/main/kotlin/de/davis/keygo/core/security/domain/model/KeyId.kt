package de.davis.keygo.core.security.domain.model

/**
 * Every Android Keystore alias this app owns.
 *
 * A sealed interface over enums rather than a constructible type: [id] and [needsAuthentication]
 * are only meaningful as a pair, and [needsAuthentication] is read once, at key creation. A caller
 * free to name an existing alias under a weaker policy could otherwise mint an auth-free key where
 * an auth-bound one was intended, so the set of aliases is closed here by construction.
 *
 * Each subtype is one kind of key, so an operation only accepts the kind it can run on: a cipher is
 * only ever made from a [CipherKeyId], a signature only from a [SigningKeyId].
 */
sealed interface KeyId {
    val id: String
    val needsAuthentication: Boolean
}

/** AES-256-GCM secret keys that wrap or encrypt other key material. */
enum class CipherKeyId(
    override val id: String,
    override val needsAuthentication: Boolean,
) : KeyId {
    /** Wraps the account's ARK for biometric unlock; this key can only be used once unlocked via biometrics */
    BiometricVaultKek("biometric_vault_kek", true),

    /** Wraps the escrowed backup passphrase; auth-free so a scheduled backup can run unattended. */
    BackupPassphraseKey("backup_passphrase_key", false),

    /**
     * Wraps the escrowed ARK copy for decrypting data for backups; auth-free for the same reason
     * as [BackupPassphraseKey].
     */
    BackupArkKey("backup_ark_key", false),
}

/** P-256 key pairs that sign with SHA256withECDSA; the private half never leaves the Keystore. */
enum class SigningKeyId(
    override val id: String,
    override val needsAuthentication: Boolean,
) : KeyId {
    /**
     * Binds sync tokens to this device (DPoP). Auth-free, so sync can refresh without a prompt, but
     * like every key only usable while the device is unlocked (API 28+), so signing fails while the
     * lock screen shows.
     */
    // TODO: reconsider setUnlockedDeviceRequired for this key once background sync exists: a sync
    //  worker running while the device is locked could not refresh.
    DpopSigningKey("dpop_signing_key", false),
}
