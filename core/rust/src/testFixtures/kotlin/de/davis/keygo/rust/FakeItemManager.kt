package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.EncryptedItemBlob
import de.davisalessandro.keygo.rust.ItemCryptoException
import de.davisalessandro.keygo.rust.ItemKey
import de.davisalessandro.keygo.rust.ItemManagerInterface
import java.security.SecureRandom

/**
 * In-memory [ItemManagerInterface] for tests.
 *
 * Encryption XORs the plaintext with a stream derived from (item key, AAD, nonce). Decryption
 * reproduces the same stream and must match a recorded plaintext; passing the wrong key or AAD
 * throws [ItemCryptoException.DecryptionFailed], exercising the authentication-failure path.
 */
class FakeItemManager : ItemManagerInterface {

    private val record = mutableMapOf<Key, ByteArray>()

    override fun createNewItemKey(): ItemKey =
        ByteArray(32).also { SecureRandom().nextBytes(it) }

    override fun encryptItemData(
        itemKey: ItemKey,
        data: ByteArray,
        aad: ByteArray,
    ): EncryptedItemBlob {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = xorStream(data, itemKey, aad, nonce)
        record[Key(itemKey.toList(), ciphertext.toList(), aad.toList(), nonce.toList())] = data
        return EncryptedItemBlob(ciphertext = ciphertext, nonce = nonce)
    }

    override fun decryptItemData(
        itemKey: ItemKey,
        blob: EncryptedItemBlob,
        aad: ByteArray,
    ): ByteArray = record[Key(itemKey.toList(), blob.ciphertext.toList(), aad.toList(), blob.nonce.toList())]
        ?: throw ItemCryptoException.DecryptionFailed()

    private fun xorStream(
        data: ByteArray,
        itemKey: ByteArray,
        aad: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        return ByteArray(data.size) { i ->
            val aadByte = if (aad.isEmpty()) 0 else aad[i % aad.size].toInt()
            val mask = itemKey[i % itemKey.size].toInt() xor
                    aadByte xor
                    nonce[i % nonce.size].toInt()
            (data[i].toInt() xor mask).toByte()
        }
    }

    private data class Key(
        val itemKey: List<Byte>,
        val ciphertext: List<Byte>,
        val aad: List<Byte>,
        val nonce: List<Byte>,
    )
}
