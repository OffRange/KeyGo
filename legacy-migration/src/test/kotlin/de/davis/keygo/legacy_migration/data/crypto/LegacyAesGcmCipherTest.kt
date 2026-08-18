package de.davis.keygo.legacy_migration.data.crypto

import de.davis.keygo.legacy_migration.data.encryptLikeV1
import kotlinx.coroutines.test.runTest
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
    fun `decrypts a blob written by v1`() = runTest {
        val plaintext = "{\"type\":1,\"username\":\"ada\"}".encodeToByteArray()

        val decrypted = cipher.decrypt(encryptLikeV1(plaintext, key), key)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypts an empty plaintext`() = runTest {
        assertContentEquals(
            byteArrayOf(),
            cipher.decrypt(encryptLikeV1(byteArrayOf(), key), key),
        )
    }

    @Test
    fun `returns null for a blob encrypted under a different key`() = runTest {
        val otherKey = newKey()

        assertNull(cipher.decrypt(encryptLikeV1(byteArrayOf(1, 2, 3), otherKey), key))
    }

    @Test
    fun `returns null for a blob shorter than the iv`() = runTest {
        assertNull(cipher.decrypt(byteArrayOf(1, 2, 3), key))
    }

    @Test
    fun `returns null for a tampered blob`() = runTest {
        val blob = encryptLikeV1("secret".encodeToByteArray(), key)
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertNull(cipher.decrypt(blob, key))
    }

    @Test
    fun `uses a twelve byte iv prefix`() = runTest {
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
