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
 * [FakeKeyWrapper] uses. Unlike [FakeKeyWrapper] though, a [FakeArkSession] is not a single shared
 * instance: backup recovers an escrowed ARK into a throwaway session distinct from the one that
 * wrapped the blob in the first place, so unwrapping has to work across instances. XOR is its own
 * inverse, so `unwrap` re-derives the same stream instead of looking anything up in memory; a
 * short tag appended to the nonce (via [tagFor]) still fails a blob wrapped under a different
 * outer key or id. A password-derived KEK is SHA-256 over (password + salt), so a wrong password
 * produces a different KEK and the unwrap fails the tag check.
 *
 * Set [failDerivation] to force derivation to throw, mirroring an Argon2 failure. Set
 * [startUnlocked] to seed the fake with an account already in place, for tests that use it as a
 * property initialiser and cannot suspend to call [createAccount] themselves.
 */
class FakeArkSession(startUnlocked: Boolean = false) : ArkSession(NoHandle) {

    var failDerivation: Boolean = false

    private var ark: ByteArray? = null

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
        if (ark.size != 32) throw ArkSessionException.KeyWrap(
            KeyWrapException.InvalidKeyLength(expected = 32UL, got = ark.size.toULong()),
        )
        this.ark = ark.copyOf()
    }

    override fun exportArk(): ByteArray = requireActive().copyOf()

    override fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ) {
        // Derive first, outside the catch: a derivation failure is Derivation, not WrongPassword.
        // Only the unwrap step below collapses to WrongPassword, mirroring the real session
        // (core/src/ark_session.rs:167-169).
        val kek = kek(password, salt)
        runCatching { unwrap(kek, wrapped, userId) }
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

    /**
     * `Session` never calls this: it exists on [ArkSessionInterface] only because Task 6 has not
     * yet deleted it. Left un-overridden, it would fall through to [ArkSession]'s real
     * implementation, which dials into JNI with a zero handle and crashes there instead of failing
     * legibly. Fail loudly here instead, so a future caller gets a clear message rather than a
     * native crash.
     */
    override fun unlock(kek: ByteArray, wrapped: WrappedKeyBlob, userId: UUID): Unit =
        error("FakeArkSession.unlock is unused: Session never calls it, and Task 6 removes it")

    private fun requireActive(): ByteArray = ark ?: throw ArkSessionException.Locked()

    private fun kek(password: String, salt: ByteArray): ByteArray {
        if (failDerivation) throw ArkSessionException.Derivation("forced")
        return MessageDigest.getInstance("SHA-256").digest(password.toByteArray() + salt)
    }

    /**
     * Wraps [innerKey] under (outerKey, id). The nonce plus a short tag ride along together in
     * [WrappedKeyBlob.nonce].
     */
    private fun wrap(outerKey: ByteArray, innerKey: ByteArray, id: UUID): WrappedKeyBlob {
        val nonce = randomBytes(NONCE_SIZE)
        val ciphertext = xorStream(innerKey, outerKey, id, nonce)
        val tag = tagFor(outerKey, id, nonce, innerKey)
        return WrappedKeyBlob(ciphertext = ciphertext, nonce = nonce + tag)
    }

    /**
     * Inverts [wrap]. XOR is its own inverse, so re-deriving the stream from (outerKey, id, the
     * stored nonce) recovers the plaintext key with no state to look up - the same math the real
     * session runs, just XOR instead of AES-GCM. The trailing tag is what turns a wrong outer key
     * or id into a thrown [KeyWrapException.UnwrapFailed] instead of a silently wrong key: without
     * it, unwrapping under the wrong key would "succeed" with garbage bytes.
     */
    private fun unwrap(outerKey: ByteArray, wrapped: WrappedKeyBlob, id: UUID): ByteArray {
        val nonce = wrapped.nonce.copyOfRange(0, NONCE_SIZE)
        val tag = wrapped.nonce.copyOfRange(NONCE_SIZE, wrapped.nonce.size)
        val candidate = xorStream(wrapped.ciphertext, outerKey, id, nonce)
        if (!tagFor(outerKey, id, nonce, candidate).contentEquals(tag)) {
            throw ArkSessionException.KeyWrap(KeyWrapException.UnwrapFailed())
        }
        return candidate
    }

    /** A short, non-cryptographic integrity tag: enough to reject a wrong key or id in tests. */
    private fun tagFor(
        outerKey: ByteArray,
        id: UUID,
        nonce: ByteArray,
        innerKey: ByteArray,
    ): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(outerKey + id.toString().toByteArray() + nonce + innerKey)
            .copyOf(TAG_SIZE)

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

        /** Length of the XOR nonce portion of [WrappedKeyBlob.nonce]; the tag follows it. */
        const val NONCE_SIZE = 12

        /** Length of the integrity tag appended after the nonce. */
        const val TAG_SIZE = 8
    }
}
