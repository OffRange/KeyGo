package de.davis.keygo.legacy_migration.data

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Byte-for-byte v1's `Cryptography.encryptWithIV`: the 12 byte GCM IV prefixed to the ciphertext in
 * one blob, AES-256-GCM, 128 bit tag.
 *
 * The one transcription of v1's wire format in this module, on purpose. A test that passes against
 * it means compatibility with the bytes real v1 installs wrote; a second copy could drift and leave
 * the tests agreeing with each other about the wrong framing.
 */
fun encryptLikeV1(plaintext: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.iv + cipher.doFinal(plaintext)
}
