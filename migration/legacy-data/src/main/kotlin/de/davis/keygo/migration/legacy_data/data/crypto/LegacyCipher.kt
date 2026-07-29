package de.davis.keygo.migration.legacy_data.data.crypto

internal fun interface LegacyCipher {

    /** Returns null when the blob cannot be decrypted for any reason. */
    fun decrypt(blob: ByteArray): ByteArray?
}
