package de.davis.keygo.core.crypto

import de.davis.keygo.core.data.crypto.CryptographicScopeImpl
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CryptographicScopeImplTest {

    // Use a fixed seed for reproducibility in tests,
    // but a new random each time can also be considered.
    private val random = Random(42)

    private val aesKey by lazy {
        // 256-bit fake key for testing only
        val keyBytes = ByteArray(32) { random.nextBytes(1)[0] }
        val secretKey = SecretKeySpec(keyBytes, "AES")
        AesKey(secretKey)
    }

    private val scope by lazy {
        CryptographicScopeImpl(aesKey)
    }

    /**
     * 1. Basic round-trip: small data.
     */
    @Test
    fun `test encrypt then decrypt small data returns original data`() = runTest {
        val originalData = "Hello, World!".toByteArray()

        val encryptedData = scope.run { originalData.encrypt() }
        val decryptedData = scope.run { encryptedData.decrypt() }

        assertContentEquals(originalData, decryptedData)
    }

    /**
     * 2. Empty data encryption-decryption.
     */
    @Test
    fun `test encrypt empty data`() = runTest {
        val originalData = ByteArray(0)

        val encryptedData = scope.run { originalData.encrypt() }
        val decryptedData = scope.run { encryptedData.decrypt() }

        assertContentEquals(originalData, decryptedData)
        assertTrue(encryptedData.data.isEmpty(), "Encrypted data should be empty for empty input")
    }

    /**
     * 3. Corrupted data should fail to decrypt.
     *    We flip one byte in the ciphertext to ensure decryption fails.
     */
    @Test
    fun `test corrupted data throws exception on decrypt`() = runTest {
        val originalData = "This data will be corrupted!".toByteArray()
        val encryptedData = scope.run { originalData.encrypt() }

        val corruptedCiphertext = encryptedData.data.clone().apply {
            val middleIndex = size / 2
            this[middleIndex] = (this[middleIndex].toInt() xor 0xFF).toByte()
        }
        val corrupted = CryptographicData(corruptedCiphertext)

        assertFailsWith<Exception> {
            runBlocking {
                scope.run { corrupted.decrypt() }
            }
        }
    }

    /**
     * 4. Repeated encryption of the same data should yield different results
     *    (because each encryption uses a new IV in AES-GCM).
     */
    @Test
    fun `test repeated encryption yields different ciphertext`() = runTest {
        val originalData = "Sensitive secret data".toByteArray()
        val results = mutableSetOf<String>()

        repeat(5) {
            val encryptedData = scope.run { originalData.encrypt() }
            results.add(encryptedData.data.joinToString(separator = ","))
        }

        assertTrue(
            results.size > 1,
            "Repeated encryption of the same data should yield different ciphertext each time"
        )
    }

    /**
     * 5. Concurrency: encrypt and decrypt in parallel coroutines.
     *    We do multiple round-trips simultaneously and ensure correctness.
     */
    @Test
    fun `test concurrent encryption and decryption`() = runTest {
        val originalData = "Concurrent encryption test".toByteArray()
        val jobs = mutableListOf<Deferred<Boolean>>()

        repeat(10) {
            jobs.add(async {
                val encryptedData = scope.run { originalData.encrypt() }
                val decryptedData = scope.run { encryptedData.decrypt() }
                // Return whether they match
                originalData.contentEquals(decryptedData)
            })
        }

        val results = jobs.awaitAll()

        assertTrue(
            results.all { it },
            "All concurrent encrypt/decrypt operations should return original data"
        )
    }

    /**
     * 6. Large data encryption-decryption test (~1MB).
     */
    @Test
    fun `test large data encryption and decryption`() = runTest {
        // 1 MB of random data
        val originalData = ByteArray(1_000_000) { random.nextBytes(1)[0] }

        val encryptedData = scope.run { originalData.encrypt() }
        val decryptedData = scope.run { encryptedData.decrypt() }

        // Then
        assertContentEquals(originalData, decryptedData)
    }

    /**
     * 7. Random data (variable lengths) test.
     *    For a bit more coverage, we can test 10 random sizes up to 65KB or so.
     */
    @Test
    fun `test random data encrypt decrypt multiple sizes`() = runTest {
        repeat(10) {
            val size = random.nextInt(1, 65_536) // up to 64KB
            val original = ByteArray(size) { random.nextBytes(1)[0] }
            val encrypted = scope.run { original.encrypt() }
            val decrypted = scope.run { encrypted.decrypt() }

            assertContentEquals(
                original,
                decrypted,
                "Random data of size=$size should match after decrypt"
            )
        }
    }
}