package de.davis.keygo.migration.legacy_data.data.crypto

import de.davis.keygo.migration.legacy_data.data.encryptLikeV1
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * The framing here is v1's, not ours. See [encryptLikeV1], which is the transcription of it these
 * tests are written against.
 */
class LegacyAesGcmCipherTest {

    private val cipher = LegacyAesGcmCipher()

    private val key: SecretKey = newKey()

    @Test
    fun `decrypts a blob written by v1`() {
        val plaintext = "{\"type\":1,\"username\":\"ada\"}".encodeToByteArray()

        val decrypted = cipher.decrypt(encryptLikeV1(plaintext, key), key)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypts an empty plaintext`() {
        assertContentEquals(
            byteArrayOf(),
            cipher.decrypt(encryptLikeV1(byteArrayOf(), key), key),
        )
    }

    @Test
    fun `returns null for a blob encrypted under a different key`() {
        val otherKey = newKey()

        assertNull(cipher.decrypt(encryptLikeV1(byteArrayOf(1, 2, 3), otherKey), key))
    }

    @Test
    fun `returns null for a blob shorter than the iv`() {
        assertNull(cipher.decrypt(byteArrayOf(1, 2, 3), key))
    }

    @Test
    fun `returns null for a tampered blob`() {
        val blob = encryptLikeV1("secret".encodeToByteArray(), key)
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertNull(cipher.decrypt(blob, key))
    }

    @Test
    fun `uses a twelve byte iv prefix`() {
        val blob = encryptLikeV1("x".encodeToByteArray(), key)
        val manual = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        }.doFinal(blob, 12, blob.size - 12)

        assertContentEquals(manual, cipher.decrypt(blob, key))
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES")
        .apply { init(256, SecureRandom()) }
        .generateKey()
}
