package de.davis.keygo.migration.legacy_data.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * The framing here is v1's, not ours: `Cryptography.encryptWithIV` prefixed the 12 byte GCM IV to
 * the ciphertext in one blob. `encryptLikeV1` below is a transcription of that method, so a passing
 * test means wire compatibility with bytes real v1 installs wrote, not merely self-consistency.
 */
class LegacyAesGcmCipherTest {

    private val key: SecretKey = KeyGenerator.getInstance("AES")
        .apply { init(256, SecureRandom()) }
        .generateKey()

    private fun encryptLikeV1(plaintext: ByteArray, withKey: SecretKey = key): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, withKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted
    }

    private fun cipherWith(secretKey: SecretKey?) =
        LegacyAesGcmCipher(object : LegacyKeyProvider {
            override fun secretKey(): SecretKey? = secretKey
        })

    @Test
    fun `decrypts a blob written by v1`() {
        val plaintext = "{\"type\":1,\"username\":\"ada\"}".encodeToByteArray()

        val decrypted = cipherWith(key).decrypt(encryptLikeV1(plaintext))

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypts an empty plaintext`() {
        assertContentEquals(byteArrayOf(), cipherWith(key).decrypt(encryptLikeV1(byteArrayOf())))
    }

    @Test
    fun `returns null when the keystore has no legacy alias`() {
        assertNull(cipherWith(null).decrypt(encryptLikeV1(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `returns null for a blob encrypted under a different key`() {
        val otherKey = KeyGenerator.getInstance("AES")
            .apply { init(256, SecureRandom()) }
            .generateKey()

        assertNull(cipherWith(key).decrypt(encryptLikeV1(byteArrayOf(1, 2, 3), otherKey)))
    }

    @Test
    fun `returns null for a blob shorter than the iv`() {
        assertNull(cipherWith(key).decrypt(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `returns null for a tampered blob`() {
        val blob = encryptLikeV1("secret".encodeToByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertNull(cipherWith(key).decrypt(blob))
    }

    @Test
    fun `uses a twelve byte iv prefix`() {
        val blob = encryptLikeV1("x".encodeToByteArray())
        val manual = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        }.doFinal(blob, 12, blob.size - 12)

        assertContentEquals(manual, cipherWith(key).decrypt(blob))
    }
}
