package de.davis.keygo.core.sync.data

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.model.SigningKeyId
import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.DpopKey
import de.davisalessandro.keygo.rust.SignException
import org.koin.core.annotation.Single
import java.math.BigInteger
import java.security.interfaces.ECPublicKey

@Single
internal class DpopKey(
    private val keyStoreManager: KeyStoreManager,
) : DpopKey {

    override fun publicKey(): ByteArray {
        val publicKey = keyStoreManager.getOrCreatePublicKey(SigningKeyId.DpopSigningKey).orThrow()
        val ecKey = publicKey as? ECPublicKey
            ?: throw SignException.Failed(reason = "the DPoP key is not an EC key")

        return ecKey.toUncompressedPoint()
    }

    override fun sign(message: ByteArray): ByteArray =
        keyStoreManager.sign(SigningKeyId.DpopSigningKey, message).orThrow()

    private fun <T> Result<T, KeyStoreManagerError>.orThrow(): T = when (this) {
        is Result.Success -> success
        is Result.Failure -> throw SignException.Failed(reason = "keystore: $error")
    }
}

private const val P256_COORDINATE_BYTES = 32
private const val SEC1_UNCOMPRESSED = 0x04.toByte()

/** SEC1 uncompressed point `04 ‖ X ‖ Y`, each coordinate left-padded to 32 bytes. */
internal fun ECPublicKey.toUncompressedPoint(): ByteArray =
    byteArrayOf(SEC1_UNCOMPRESSED) + w.affineX.toCoordinate() + w.affineY.toCoordinate()

/**
 * [BigInteger.toByteArray] is minimal two's complement: a 33rd sign byte when the top bit is set,
 * fewer than 32 bytes when the coordinate has leading zeros.
 */
private fun BigInteger.toCoordinate(): ByteArray {
    val bytes = toByteArray()
    return when {
        bytes.size > P256_COORDINATE_BYTES -> bytes.copyOfRange(
            bytes.size - P256_COORDINATE_BYTES,
            bytes.size
        )

        else -> ByteArray(P256_COORDINATE_BYTES - bytes.size) + bytes
    }
}
