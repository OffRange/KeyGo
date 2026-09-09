package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.ArkSessionInterface
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.NoHandle
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * In-memory [ArkSessionInterface] for tests. Extends the generated [ArkSession] through its
 * `NoHandle` test constructor rather than implementing the interface directly: a caller can pass
 * this into anything that expects the concrete `ArkSession` (backup's `BackupCredential.Session`,
 * for one), and every generated member of that class is a plain `override fun`, so all of them are
 * free to be replaced here. `ArkSession(NoHandle)` allocates no Rust object and never touches the
 * native library, so this stays a normal JVM unit test fixture despite subclassing a UniFFI type.
 *
 * Wrapping XORs the key with a stream derived from (outer key, id, nonce), the same scheme
 * [FakeKeyWrapper] uses, so a blob round-trips only under the outer key and id it was wrapped
 * with. A password-derived KEK is SHA-256 over (password + salt), so a wrong password produces a
 * different KEK and the unwrap fails.
 *
 * Set [failDerivation] to force derivation to throw, mirroring an Argon2 failure. Set
 * [startUnlocked] to seed the fake with an account already in place, for tests that use it as a
 * property initialiser and cannot suspend to call [createAccount] themselves.
 */
class FakeArkSession(startUnlocked: Boolean = false) : ArkSession(NoHandle) {

    var failDerivation: Boolean = false

    private var ark: ByteArray? = null
    private val wrapRecord = mutableMapOf<Triple<List<Byte>, List<Byte>, UUID>, ByteArray>()

    init {
        if (startUnlocked) createAccount(SEED_PASSWORD)
    }

    override fun createAccount(password: String): NewAccount {
        val ark = randomKey()
        val vaultKey = randomKey()
        val userId = UUID.randomUUID()
        val vaultId = UUID.randomUUID()
        val salt = randomBytes(16)

        val passwordWrappedArk = wrap(kek(password, salt), ark, userId)
        val wrappedVaultKey = wrap(ark, vaultKey, vaultId)

        this.ark = ark

        return NewAccount(
            userId = userId,
            salt = salt,
            passwordWrappedArk = passwordWrappedArk,
            vaultId = vaultId,
            wrappedVaultKey = wrappedVaultKey,
        )
    }

    override fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ) {
        ark = unwrap(kek(password, salt), wrapped, userId)
    }

    override fun unlockWithArk(ark: ByteArray) {
        if (ark.size != 32) {
            throw ArkSessionException.KeyWrap(
                KeyWrapException.InvalidKeyLength(expected = 32UL, got = ark.size.toULong()),
            )
        }
        this.ark = ark.copyOf()
    }

    override fun exportArk(): ByteArray = requireActive().copyOf()

    override fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ) {
        runCatching { unwrap(kek(password, salt), wrapped, userId) }
            .onFailure { throw ArkSessionException.WrongPassword() }
    }

    override fun verifyArk(ark: ByteArray): Boolean = this.ark?.contentEquals(ark) == true

    override fun rewrapForNewPassword(newPassword: String, userId: UUID): PasswordWrapped {
        val ark = requireActive()
        val salt = randomBytes(16)
        return PasswordWrapped(salt = salt, wrapped = wrap(kek(newPassword, salt), ark, userId))
    }

    override fun wrapVaultKey(vaultKey: ByteArray, vaultId: UUID): WrappedKeyBlob =
        wrap(requireActive(), vaultKey, vaultId)

    override fun unwrapVaultKey(wrapped: WrappedKeyBlob, vaultId: UUID): ByteArray =
        unwrap(requireActive(), wrapped, vaultId)

    override fun isActive(): Boolean = ark != null

    override fun end() {
        ark = null
    }

    private fun requireActive(): ByteArray = ark ?: throw ArkSessionException.Locked()

    private fun kek(password: String, salt: ByteArray): ByteArray {
        if (failDerivation) throw ArkSessionException.Derivation("forced")
        return MessageDigest.getInstance("SHA-256").digest(password.toByteArray() + salt)
    }

    private fun wrap(outerKey: ByteArray, innerKey: ByteArray, id: UUID): WrappedKeyBlob {
        val nonce = randomBytes(12)
        val ciphertext = xorStream(innerKey, outerKey, id, nonce)
        wrapRecord[Triple(outerKey.toList(), ciphertext.toList(), id)] = innerKey.copyOf()
        return WrappedKeyBlob(ciphertext = ciphertext, nonce = nonce)
    }

    private fun unwrap(outerKey: ByteArray, wrapped: WrappedKeyBlob, id: UUID): ByteArray =
        wrapRecord[Triple(outerKey.toList(), wrapped.ciphertext.toList(), id)]?.copyOf()
            ?: throw ArkSessionException.KeyWrap(KeyWrapException.UnwrapFailed())

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

    private fun randomKey(): ByteArray = randomBytes(32)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    private companion object {
        /** Password used to seed the account when [startUnlocked] is set. Value is arbitrary. */
        const val SEED_PASSWORD = "fake-ark-session-seed"
    }
}
