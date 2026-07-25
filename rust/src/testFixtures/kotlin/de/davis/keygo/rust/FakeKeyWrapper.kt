package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.AccountRootKey
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.ItemKey
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.RootKek
import de.davisalessandro.keygo.rust.VaultKey
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import java.security.SecureRandom
import java.util.UUID

/**
 * In-memory [KeyWrapperInterface] for tests.
 *
 * Wrapping XORs the plaintext key with a stream derived from (outer key, id, nonce) so that
 * wrap/unwrap round-trips correctly when the same outer key and id are supplied. Unwrapping
 * with a different outer key or id yields garbage; every `unwrap*` call throws
 * [KeyWrapException.UnwrapFailed] when the result does not match a recorded ciphertext, which
 * is sufficient to exercise the wrong-password / wrong-key paths in use case tests.
 *
 * Set [failUnwrapItemForId] to force [unwrapItemKey] to throw the supplied exception whenever
 * it is called for an item whose id matches the recorded id.
 */
class FakeKeyWrapper : KeyWrapperInterface {

    var failUnwrapItemForId: Pair<UUID, KeyWrapException>? = null

    private val wrapRecord = mutableMapOf<Triple<List<Byte>, List<Byte>, UUID>, ByteArray>()

    override fun wrapAccountRootKey(
        kek: RootKek,
        ark: AccountRootKey,
        userId: UUID,
    ): WrappedKeyBlob = wrap(outerKey = kek, innerKey = ark, id = userId)

    override fun unwrapAccountRootKey(
        kek: RootKek,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): AccountRootKey = unwrap(outerKey = kek, wrapped = wrapped, id = userId)

    override fun wrapVaultKey(
        ark: AccountRootKey,
        vaultKey: VaultKey,
        vaultId: UUID,
    ): WrappedKeyBlob = wrap(outerKey = ark, innerKey = vaultKey, id = vaultId)

    override fun unwrapVaultKey(
        ark: AccountRootKey,
        wrapped: WrappedKeyBlob,
        vaultId: UUID,
    ): VaultKey = unwrap(outerKey = ark, wrapped = wrapped, id = vaultId)

    override fun wrapItemKey(
        vaultKey: VaultKey,
        itemKey: ItemKey,
        aad: ItemAad,
    ): WrappedKeyBlob = wrap(outerKey = vaultKey, innerKey = itemKey, id = aadId(aad))

    override fun unwrapItemKey(
        vaultKey: VaultKey,
        wrapped: WrappedKeyBlob,
        aad: ItemAad,
    ): ItemKey {
        failUnwrapItemForId?.let { (failingItemId, error) ->
            if (aad.itemId == failingItemId) throw error
        }
        return unwrap(outerKey = vaultKey, wrapped = wrapped, id = aadId(aad))
    }

    private fun aadId(aad: ItemAad): UUID =
        UUID(
            aad.itemId.mostSignificantBits xor aad.vaultId.mostSignificantBits,
            aad.itemId.leastSignificantBits xor aad.vaultId.leastSignificantBits,
        )

    private fun wrap(outerKey: ByteArray, innerKey: ByteArray, id: UUID): WrappedKeyBlob {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = xorStream(innerKey, outerKey, id, nonce)
        // Store a copy: the real UniFFI wrapper copies the key across the FFI boundary, so callers
        // are free to scrub the array they passed in. Aliasing it here would let a caller's later
        // `fill(0)` zero the recorded key material.
        wrapRecord[Triple(outerKey.toList(), ciphertext.toList(), id)] = innerKey.copyOf()
        return WrappedKeyBlob(ciphertext = ciphertext, nonce = nonce)
    }

    private fun unwrap(outerKey: ByteArray, wrapped: WrappedKeyBlob, id: UUID): ByteArray {
        val recorded = wrapRecord[Triple(outerKey.toList(), wrapped.ciphertext.toList(), id)]
            ?: throw KeyWrapException.UnwrapFailed()
        // Return a fresh array, mirroring the real wrapper: scrubbing the unwrapped key must not
        // corrupt the recorded ciphertext so a subsequent unwrap still round-trips.
        return recorded.copyOf()
    }

    private fun xorStream(
        innerKey: ByteArray,
        outerKey: ByteArray,
        id: UUID,
        nonce: ByteArray,
    ): ByteArray {
        val idBytes = id.toString().toByteArray()
        return ByteArray(innerKey.size) { i ->
            val mask = outerKey[i % outerKey.size].toInt() xor
                    idBytes[i % idBytes.size].toInt() xor
                    nonce[i % nonce.size].toInt()
            (innerKey[i].toInt() xor mask).toByte()
        }
    }
}
