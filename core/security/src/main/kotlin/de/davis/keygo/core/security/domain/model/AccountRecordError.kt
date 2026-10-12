package de.davis.keygo.core.security.domain.model

sealed interface AccountRecordError {
    data object NoActiveSession : AccountRecordError

    /** The record's key did not unwrap: wrong namespace, another account's key, or a damaged key. */
    data object KeyUnwrapFailed : AccountRecordError

    /** The key unwrapped but the data did not decrypt: damaged ciphertext or the wrong kind. */
    data object Corrupted : AccountRecordError
}
