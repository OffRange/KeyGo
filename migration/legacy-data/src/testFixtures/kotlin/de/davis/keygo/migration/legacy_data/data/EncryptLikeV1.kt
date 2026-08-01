package de.davis.keygo.migration.legacy_data.data

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * The framing here is v1's, not ours: `Cryptography.encryptWithIV` prefixed the 12 byte GCM IV to
 * the ciphertext in one blob. This is a transcription of that method, so blobs built with it are
 * wire compatible with what real v1 installs wrote, not merely self-consistent with
 * `LegacyAesGcmCipher`.
 */
internal fun encryptLikeV1(plaintext: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plaintext)
    return iv + encrypted
}
