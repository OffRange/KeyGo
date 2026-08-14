package de.davis.keygo.legacy_migration.domain.crypto

import javax.crypto.SecretKey

internal fun interface LegacyCipher {

    /**
     * Returns null when the blob cannot be decrypted for any reason.
     *
     * The key is handed in rather than looked up per blob: every blob in the file is under the one
     * v1 alias, and a run resolves it once. Looking it up per blob costs a binder round trip to the
     * Keystore for every row, twice over, to arrive back at the same key.
     */
    fun decrypt(blob: ByteArray, key: SecretKey): ByteArray?
}
