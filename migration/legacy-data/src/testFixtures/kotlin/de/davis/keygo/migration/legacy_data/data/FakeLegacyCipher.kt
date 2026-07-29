package de.davis.keygo.migration.legacy_data.data

import de.davis.keygo.migration.legacy_data.data.FakeLegacyCipher.Companion.FAIL
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyCipher

/**
 * Reversible stand-in for the Keystore cipher: a blob decrypts to itself, so a test seeds the
 * plaintext it wants to read back and never needs a Keystore.
 *
 * Two ways to model a blob that will not open, because the two failures enter the import at
 * different points. A blob prefixed with [FAIL] is a row the repository cannot decrypt at all,
 * which it records as a row failure and keeps reading past. [failFor] names one exact blob, which
 * is how the nested password inside an otherwise readable row is failed on its own.
 */
internal class FakeLegacyCipher(private val failFor: ByteArray? = null) : LegacyCipher {

    override fun decrypt(blob: ByteArray): ByteArray? = when {
        failFor != null && blob.contentEquals(failFor) -> null
        blob.decodeToString().startsWith(FAIL) -> null
        else -> blob
    }

    companion object {
        const val FAIL = "!!UNDECRYPTABLE!!"

        /** Fails every blob, which is how a whole run under a gone v1 key is modelled. */
        val Failing = LegacyCipher { null }
    }
}
