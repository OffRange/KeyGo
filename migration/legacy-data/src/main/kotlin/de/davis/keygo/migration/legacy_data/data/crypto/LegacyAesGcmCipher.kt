package de.davis.keygo.migration.legacy_data.data.crypto

import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyRepository
import org.koin.core.annotation.Single
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Reverses v1's `Cryptography.encryptAES`, which wrote `IV(12) || AES-256-GCM ciphertext` with a
 * 128 bit tag under the `password_manager_skey` Keystore alias.
 */
@Single
internal class LegacyAesGcmCipher(
    private val keyRepository: LegacyKeyRepository,
) : LegacyCipher {

    override fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size <= IV_SIZE) return null
        val key = keyRepository.secretKey() ?: return null

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
