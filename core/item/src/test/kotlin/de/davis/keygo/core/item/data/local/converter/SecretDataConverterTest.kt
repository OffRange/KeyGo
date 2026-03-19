package de.davis.keygo.core.item.data.local.converter

import de.davis.keygo.core.item.domain.model.SecretData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretDataConverterTest {

    @Test
    fun `round trip preserves data`() {
        val original = SecretData(
            data = byteArrayOf(10, 20, 30, 40, 50),
            iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )

        val bytes = SecretDataConverter.fromSecretDataString(original)
        val restored = SecretDataConverter.fromByteArrayToSecretDataString(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `null input returns null on serialize`() {
        assertNull(SecretDataConverter.fromSecretDataString(null))
    }

    @Test
    fun `null input returns null on deserialize`() {
        assertNull(SecretDataConverter.fromByteArrayToSecretDataString(null))
    }

    @Test
    fun `empty byte array returns null on deserialize`() {
        assertNull(SecretDataConverter.fromByteArrayToSecretDataString(byteArrayOf()))
    }

    @Test
    fun `round trip with empty data`() {
        val original = SecretData.EMPTY_STRING

        val bytes = SecretDataConverter.fromSecretDataString(original)
        val restored = SecretDataConverter.fromByteArrayToSecretDataString(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `round trip with large IV`() {
        val iv = ByteArray(256) { it.toByte() }
        val original = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = iv,
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )

        val bytes = SecretDataConverter.fromSecretDataString(original)
        val restored = SecretDataConverter.fromByteArrayToSecretDataString(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `invalid type id returns null`() {
        // Byte 0xFF is not a valid DecryptedDataType id
        val invalidBytes = byteArrayOf(0xFF.toByte(), 0, 1, 9)
        assertNull(SecretDataConverter.fromByteArrayToSecretDataString(invalidBytes))
    }

    @Test
    fun `serialized format is type byte plus iv length plus iv plus data`() {
        val data = byteArrayOf(10, 20)
        val iv = byteArrayOf(1, 2, 3)
        val original = SecretData(
            data = data,
            iv = iv,
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )

        val bytes = SecretDataConverter.fromSecretDataString(original)!!

        // First byte: type id
        assertEquals(SecretData.DecryptedDataType.StringType.uniqueId, bytes[0])
        // Next 2 bytes: IV length (big-endian short = 3)
        assertEquals(0.toByte(), bytes[1])
        assertEquals(3.toByte(), bytes[2])
        // Next 3 bytes: IV
        assertTrue(iv.contentEquals(bytes.sliceArray(3..5)))
        // Remaining: data
        assertTrue(data.contentEquals(bytes.sliceArray(6..7)))
    }
}
