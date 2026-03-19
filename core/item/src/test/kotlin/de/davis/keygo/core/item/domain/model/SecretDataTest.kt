package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretDataTest {

    @Test
    fun `equality uses content comparison for ByteArrays`() {
        val a = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        val b = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when data differs`() {
        val a = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        val b = SecretData(
            data = byteArrayOf(9, 9, 9),
            iv = byteArrayOf(4, 5, 6),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `inequality when iv differs`() {
        val a = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        val b = SecretData(
            data = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(9, 9, 9),
            decryptedDataType = SecretData.DecryptedDataType.StringType
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `EMPTY_STRING has empty arrays`() {
        val empty = SecretData.EMPTY_STRING
        assertTrue(empty.data.isEmpty())
        assertTrue(empty.iv.isEmpty())
        assertEquals(SecretData.DecryptedDataType.StringType, empty.decryptedDataType)
    }

    @Test
    fun `StringType encodes and decodes correctly`() {
        val type = SecretData.DecryptedDataType.StringType
        val original = "Hello, World!"
        val encoded = type.encode(original)
        val decoded = type.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StringType handles empty string`() {
        val type = SecretData.DecryptedDataType.StringType
        val encoded = type.encode("")
        val decoded = type.decode(encoded)
        assertEquals("", decoded)
    }

    @Test
    fun `getById returns StringType for id 0x1`() {
        val type = SecretData.DecryptedDataType.getById(0x1)
        assertEquals(SecretData.DecryptedDataType.StringType, type)
    }

    @Test
    fun `getById returns null for unknown id`() {
        assertNull(SecretData.DecryptedDataType.getById(0x99.toByte()))
    }

    @Test
    fun `getDecryptedDataType returns StringType for String`() {
        val type = SecretData.DecryptedDataType.getDecryptedDataType<String>()
        assertEquals(SecretData.DecryptedDataType.StringType, type)
    }

    @Test
    fun `getDecryptedDataType throws for unsupported type`() {
        assertFailsWith<IllegalArgumentException> {
            SecretData.DecryptedDataType.getDecryptedDataType<Int>()
        }
    }
}
