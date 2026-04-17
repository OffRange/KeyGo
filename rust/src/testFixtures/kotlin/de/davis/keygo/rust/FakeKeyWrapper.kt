package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.AccountRootKey
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
 * Wrapping XORs the plaintext key with a stream derived from (kek/ark, id, nonce) so that
 * wrap/unwrap round-trips correctly when the same KEK and id are supplied. Unwrapping with
 * a different KEK or id yields garbage — [unwrapAccountRootKey] and [unwrapVaultKey] throw
 * [KeyWrapException.UnwrapFailed] when the result does not match a recorded ciphertext, which
 * is sufficient to exercise the wrong-password path in use case tests.
 */
class FakeKeyWrapper : KeyWrapperInterface {

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

    private fun wrap(outerKey: ByteArray, innerKey: ByteArray, id: UUID): WrappedKeyBlob {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = xorStream(innerKey, outerKey, id, nonce)
        wrapRecord[Triple(outerKey.toList(), ciphertext.toList(), id)] = innerKey
        return WrappedKeyBlob(ciphertext = ciphertext, nonce = nonce)
    }

    private fun unwrap(outerKey: ByteArray, wrapped: WrappedKeyBlob, id: UUID): ByteArray {
        val recorded = wrapRecord[Triple(outerKey.toList(), wrapped.ciphertext.toList(), id)]
            ?: throw KeyWrapException.UnwrapFailed()
        return recorded
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
