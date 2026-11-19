package de.davis.keygo.core.sync.data

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.model.SigningKeyId
import de.davisalessandro.keygo.rust.SignException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DpopKeyTest {

    private val keyStoreManager = FakeKeyStoreManager()
    private val dpopKey = DpopKey(keyStoreManager)

    @Test
    fun `the public key is a 65 byte uncompressed P-256 point`() {
        val point = dpopKey.publicKey()

        assertEquals(65, point.size)
        assertEquals(0x04.toByte(), point[0])
    }

    @Test
    fun `the public key stays the same across calls`() {
        assertContentEquals(dpopKey.publicKey(), dpopKey.publicKey())
    }

    @Test
    fun `a signature verifies under the published public key`() {
        val message = "header.payload".encodeToByteArray()

        val signature = dpopKey.sign(message)
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(decodePoint(dpopKey.publicKey()))
            update(message)
            verify(signature)
        }

        assertTrue(verified)
    }

    @Test
    fun `the key lives under the DPoP alias`() {
        dpopKey.publicKey()

        assertTrue(SigningKeyId.DpopSigningKey in keyStoreManager.keyPairs)
    }

    @Test
    fun `a keystore failure surfaces as a SignException`() {
        keyStoreManager.failure = KeyStoreManagerError.KeyInvalidated

        assertFailsWith<SignException.Failed> { dpopKey.publicKey() }
        assertFailsWith<SignException.Failed> { dpopKey.sign(byteArrayOf(1)) }
    }

    @Test
    fun `coordinates with leading zeros are padded to 32 bytes`() {
        dpopKey.publicKey()
        val reference = keyStoreManager.keyPairs.getValue(SigningKeyId.DpopSigningKey).public as ECPublicKey
        val small = object : ECPublicKey by reference {
            override fun getW(): ECPoint = ECPoint(BigInteger.ONE, BigInteger.TWO)
        }

        val point = small.toUncompressedPoint()

        assertEquals(65, point.size)
        assertContentEquals(ByteArray(31) + byteArrayOf(1), point.copyOfRange(1, 33))
        assertContentEquals(ByteArray(31) + byteArrayOf(2), point.copyOfRange(33, 65))
    }

    private fun decodePoint(point: ByteArray): ECPublicKey {
        val params = (keyStoreManager.keyPairs.getValue(SigningKeyId.DpopSigningKey).public as ECPublicKey).params
        val x = BigInteger(1, point.copyOfRange(1, 33))
        val y = BigInteger(1, point.copyOfRange(33, 65))
        return KeyFactory.getInstance("EC")
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }
}
