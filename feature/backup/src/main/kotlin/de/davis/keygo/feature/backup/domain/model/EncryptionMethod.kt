package de.davis.keygo.feature.backup.domain.model

/** How a JSON backup is sealed. Irrelevant for CSV (plaintext by design). */
enum class EncryptionMethod {
    /** Sealed with a user-chosen passphrase; restorable anywhere. */
    Passphrase,

    /** Sealed with the Account Root Key; restorable only into the same account. */
    Ark,
}
