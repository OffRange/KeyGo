package de.davis.keygo.core.identity.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WrappedKeyDataTest {

    @Test
    fun `PasswordWrappedKeyData is valid when all fields non-empty`() {
        val data = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        assertTrue(data.isValid())
    }

    @Test
    fun `PasswordWrappedKeyData is invalid when key is empty`() {
        val data = PasswordWrappedArk(
            key = byteArrayOf(),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        assertFalse(data.isValid())
    }

    @Test
    fun `PasswordWrappedKeyData is invalid when keyIV is empty`() {
        val data = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(),
            salt = byteArrayOf(7, 8, 9)
        )
        assertFalse(data.isValid())
    }

    @Test
    fun `PasswordWrappedKeyData is invalid when salt is empty`() {
        val data = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf()
        )
        assertFalse(data.isValid())
    }

    @Test
    fun `PasswordWrappedKeyData equality uses content comparison`() {
        val data1 = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        val data2 = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        assertTrue(data1 == data2)
        assertTrue(data1.hashCode() == data2.hashCode())
    }

    @Test
    fun `PasswordWrappedKeyData inequality when key differs`() {
        val data1 = PasswordWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        val data2 = PasswordWrappedArk(
            key = byteArrayOf(9, 8, 7),
            keyIV = byteArrayOf(4, 5, 6),
            salt = byteArrayOf(7, 8, 9)
        )
        assertFalse(data1 == data2)
    }

    @Test
    fun `BiometricWrappedKeyData is valid when all fields non-empty`() {
        val data = BiometricWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6)
        )
        assertTrue(data.isValid())
    }

    @Test
    fun `BiometricWrappedKeyData is invalid when key is empty`() {
        val data = BiometricWrappedArk(
            key = byteArrayOf(),
            keyIV = byteArrayOf(4, 5, 6)
        )
        assertFalse(data.isValid())
    }

    @Test
    fun `BiometricWrappedKeyData is invalid when keyIV is empty`() {
        val data = BiometricWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf()
        )
        assertFalse(data.isValid())
    }

    @Test
    fun `BiometricWrappedKeyData equality uses content comparison`() {
        val data1 = BiometricWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6)
        )
        val data2 = BiometricWrappedArk(
            key = byteArrayOf(1, 2, 3),
            keyIV = byteArrayOf(4, 5, 6)
        )
        assertTrue(data1 == data2)
        assertTrue(data1.hashCode() == data2.hashCode())
    }
}
