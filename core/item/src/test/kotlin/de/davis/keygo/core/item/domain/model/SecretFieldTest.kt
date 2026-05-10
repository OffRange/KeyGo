package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretFieldTest {

    data class TestSecretField(
        override val payload: EncryptedPayload = EncryptedPayload(
            ciphertext = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
        )
    ) : SecretField<String> {
        override val blueprint: SecretBlueprint<String, out SecretField<String>> = TestSecretField

        companion object : SecretBlueprint<String, TestSecretField>() {
            override val label: String = "test_label"
            override val codec: SecretCodec<String> = SecretCodec.StringCodec

            override fun createField(payload: EncryptedPayload): TestSecretField =
                TestSecretField(payload)
        }
    }

    @Test
    fun `equality uses content comparison for ByteArrays`() {
        val a = TestSecretField()
        val b = TestSecretField()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when data differs`() {
        val a = TestSecretField()
        val b = TestSecretField(
            EncryptedPayload(
                ciphertext = byteArrayOf(9, 9, 9),
                iv = byteArrayOf(4, 5, 6),
            )
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `inequality when iv differs`() {
        val a = TestSecretField()
        val b = TestSecretField(
            EncryptedPayload(
                ciphertext = byteArrayOf(1, 2, 3),
                iv = byteArrayOf(9, 9, 9),
            )
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `EncryptedPayload#EMPTY has empty arrays`() {
        val empty = EncryptedPayload.EMPTY
        assertTrue(empty.ciphertext.isEmpty())
        assertTrue(empty.iv.isEmpty())
    }

    @Test
    fun `StringCodec encodes and decodes correctly`() {
        val codec = SecretCodec.StringCodec
        val original = "Hello, World!"
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StringCodec handles empty string`() {
        val codec = SecretCodec.StringCodec
        val encoded = codec.encode("")
        val decoded = codec.decode(encoded)
        assertEquals("", decoded)
    }

    @Test
    fun `ByteArrayCodec encodes and decodes correctly`() {
        val codec = SecretCodec.ByteArrayCodec
        val original = "Hello, World!".encodeToByteArray()
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `ByteArrayCodec handles empty string`() {
        val codec = SecretCodec.ByteArrayCodec
        val encoded = codec.encode(byteArrayOf())
        val decoded = codec.decode(encoded)
        assertTrue(decoded.isEmpty())
    }
}
