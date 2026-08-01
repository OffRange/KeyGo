package de.davis.keygo.feature.backup.domain.model

/** Irrelevant for CSV, which is plaintext by design. */
enum class EncryptionMethod {
    /** Sealed with a user-chosen passphrase; restorable anywhere. */
    Passphrase,

    /** Sealed with the Account Root Key; restorable only into the same account. */
    Ark,
}
