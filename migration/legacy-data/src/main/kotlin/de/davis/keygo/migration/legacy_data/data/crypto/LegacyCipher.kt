package de.davis.keygo.migration.legacy_data.data.crypto

import org.koin.core.annotation.Single
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal interface LegacyCipher {

    /** Returns null when the blob cannot be decrypted for any reason. */
    fun decrypt(blob: ByteArray): ByteArray?
}

/**
 * Reverses v1's `Cryptography.encryptAES`, which wrote `IV(12) || AES-256-GCM ciphertext` with a
 * 128 bit tag under the `password_manager_skey` Keystore alias.
 */
@Single
internal class LegacyAesGcmCipher(
    private val keyProvider: LegacyKeyProvider,
) : LegacyCipher {

    override fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size <= IV_SIZE) return null
        val key = keyProvider.secretKey() ?: return null

        return runCatching {
            Cipher.getInstance(TRANSFORMATION)
                .apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
                }
                .doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
        }.getOrNull()
    }

    private companion object {
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
