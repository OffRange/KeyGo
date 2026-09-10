package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.NoHandle
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import java.util.UUID

/**
 * A [FakeArkSession] that also keeps the very arrays crossing the two ARK doors, so a test can
 * watch the caller wipe them afterwards.
 *
 * The fake copies on both doors, which is the right default and matches what Rust does. It also
 * makes a wipe unobservable: the caller zeroes its own array while the fake's copy stays intact.
 * [exportArk] and [unlockWithArk] are the only places ARK bytes reach the JVM, and so the only
 * places a Kotlin caller can leave key material resident, which is worth asserting on directly.
 *
 * Everything else delegates to a real [FakeArkSession], so this behaves like one in every other
 * respect. Extends the generated class through uniffi's `NoHandle` constructor exactly as
 * [FakeArkSession] does: no Rust object is allocated and the native library is never touched.
 */
class RecordingArkSession(startUnlocked: Boolean = false) : ArkSession(NoHandle) {

    private val delegate = FakeArkSession(startUnlocked)

    /** Makes [unlockWithArk] throw, but only after it has recorded what it was handed. */
    var failUnlock: Boolean = false

    /** Forwards to the delegate, so a test can force a derivation failure as usual. */
    var failDerivation: Boolean
        get() = delegate.failDerivation
        set(value) {
            delegate.failDerivation = value
        }

    /** The array the last [unlockWithArk] was given, kept rather than copied. */
    var handedOver: ByteArray? = null
        private set

    /** Every array [exportArk] has handed out. Each one is the caller's to wipe. */
    val exported = mutableListOf<ByteArray>()

    /** The one array [exportArk] handed out, failing loudly on any other number of calls. */
    fun onlyExported(): ByteArray = exported.singleOrNull()
        ?: error("expected exactly one exportArk call, got ${exported.size}")

    override fun exportArk(): ByteArray = delegate.exportArk().also { exported += it }

    override fun unlockWithArk(ark: ByteArray) {
        handedOver = ark
        if (failUnlock) throw ArkSessionException.Locked()
        delegate.unlockWithArk(ark)
    }

    override fun createAccount(password: String): NewAccount = delegate.createAccount(password)

    override fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ) = delegate.unlockWithPassword(password, salt, wrapped, userId)

    override fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ) = delegate.verifyPassword(password, salt, wrapped, userId)

    override fun verifyArk(ark: ByteArray): Boolean = delegate.verifyArk(ark)

    override fun rewrapForNewPassword(newPassword: String, userId: UUID): PasswordWrapped =
        delegate.rewrapForNewPassword(newPassword, userId)

    override fun wrapVaultKey(vaultKey: ByteArray, vaultId: UUID): WrappedKeyBlob =
        delegate.wrapVaultKey(vaultKey, vaultId)

    override fun unwrapVaultKey(wrapped: WrappedKeyBlob, vaultId: UUID): ByteArray =
        delegate.unwrapVaultKey(wrapped, vaultId)

    override fun unlock(kek: ByteArray, wrapped: WrappedKeyBlob, userId: UUID): Unit =
        delegate.unlock(kek, wrapped, userId)

    override fun isActive(): Boolean = delegate.isActive()

    override fun end() = delegate.end()
}
