package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.FakeArkSession
import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.NoHandle
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionTest {

    private val session = Session(FakeArkSession())

    @Test
    fun `starts locked`() = runTest {
        assertFalse(session.isActive.value)
    }

    @Test
    fun `createAccount leaves the session active`() = runTest {
        val account = session.createAccount("hunter2").getOrNull()

        assertTrue(session.isActive.value)
        assertEquals(32, account?.wrappedVaultKey?.ciphertext?.size)
    }

    @Test
    fun `unlockWithPassword activates the session`() = runTest {
        val account = checkNotNull(session.createAccount("hunter2").getOrNull())
        session.endSession()

        val result = session.unlockWithPassword(
            password = "hunter2",
            salt = account.salt,
            wrapped = account.passwordWrappedArk,
            userId = account.userId,
        )

        assertIs<Result.Success<Unit, SessionError>>(result)
        assertTrue(session.isActive.value)
    }

    /**
     * Unlocking reports the unwrap failure itself, where verifying collapses the same failure to
     * [SessionError.WrongPassword] (see `verifyPassword rejects the wrong password`). That
     * asymmetry is deliberate and shipped: the unlock screen shows an unwrap failure, and only the
     * change-password screen claims to know the password was wrong. The error is asserted by value
     * rather than by type, so a regression to `WrongPassword` here cannot pass unnoticed. The
     * `v1=` prefix is pinned separately, by the `describe` test below.
     */
    @Test
    fun `a wrong password keeps the session locked`() = runTest {
        val account = checkNotNull(session.createAccount("hunter2").getOrNull())
        session.endSession()

        val result = session.unlockWithPassword(
            password = "wrong",
            salt = account.salt,
            wrapped = account.passwordWrappedArk,
            userId = account.userId,
        )

        assertEquals(SessionError.KeyWrap("unwrap failed"), (result as Result.Failure).error)
        assertFalse(session.isActive.value)
    }

    @Test
    fun `endSession deactivates and locks out ark access`() = runTest {
        session.createAccount("hunter2")
        session.endSession()

        assertFalse(session.isActive.value)
        assertEquals(SessionError.Locked, (session.exportArk() as Result.Failure).error)
    }

    @Test
    fun `vault keys round trip through the session`() = runTest {
        session.createAccount("hunter2")
        val vaultId = UUID.randomUUID()
        val vaultKey = ByteArray(32) { it.toByte() }

        val wrapped = checkNotNull(session.wrapVaultKey(vaultKey, vaultId).getOrNull())
        val unwrapped = session.unwrapVaultKey(wrapped, vaultId).getOrNull()

        assertContentEquals(vaultKey, unwrapped)
    }

    @Test
    fun `unwrapping a vault key while locked fails with Locked`() = runTest {
        val result = session.unwrapVaultKey(
            wrapped = WrappedKeyBlob(ByteArray(32), ByteArray(12)),
            vaultId = UUID.randomUUID(),
        )

        assertEquals(SessionError.Locked, (result as Result.Failure).error)
    }

    @Test
    fun `exportArk and unlockWithArk round trip between sessions`() = runTest {
        session.createAccount("hunter2")
        val exported = checkNotNull(session.exportArk().getOrNull())

        val second = Session(FakeArkSession())
        second.unlockWithArk(exported)

        assertTrue(second.isActive.value)
        assertTrue(second.verifyArk(exported))
    }

    @Test
    fun `rewrapForNewPassword produces a blob the new password unlocks`() = runTest {
        val account = checkNotNull(session.createAccount("hunter2").getOrNull())

        val rewrapped =
            checkNotNull(session.rewrapForNewPassword("new-password", account.userId).getOrNull())
        session.endSession()

        val result = session.unlockWithPassword(
            password = "new-password",
            salt = rewrapped.salt,
            wrapped = rewrapped.wrapped,
            userId = account.userId,
        )

        assertIs<Result.Success<Unit, SessionError>>(result)
    }

    /**
     * The backup escrow shape: `BackupArkUnlocker` recovers the escrowed ARK into a throwaway
     * session and unwraps vault keys the app session wrapped. Unwrapping has to work across two
     * sessions holding the same ARK, and must still refuse the wrong vault id.
     */
    @Test
    fun `a vault key wrapped in one session unwraps in another holding the same ark`() = runTest {
        session.createAccount("hunter2")
        val vaultId = UUID.randomUUID()
        val vaultKey = ByteArray(32) { (it * 7).toByte() }
        val wrapped = checkNotNull(session.wrapVaultKey(vaultKey, vaultId).getOrNull())

        val exported = checkNotNull(session.exportArk().getOrNull())
        val recovered = Session(FakeArkSession())
        recovered.unlockWithArk(exported)

        assertContentEquals(vaultKey, recovered.unwrapVaultKey(wrapped, vaultId).getOrNull())
        assertIs<Result.Failure<ByteArray, SessionError>>(
            recovered.unwrapVaultKey(wrapped, UUID.randomUUID()),
        )
    }

    /**
     * Every [KeyWrapException] variant reports its own fields. The generated `message` getter
     * renders [KeyWrapException.Other] as `"v1=<payload>"`, and `Other` is the catch-all arm of
     * `From<CryptoError>` on the Rust side, so reading `message` would leak that prefix into a
     * real error payload.
     */
    @Test
    fun `key wrap errors carry their own message and never the generated v1 prefix`() = runTest {
        val cases = listOf(
            KeyWrapException.Other("disk on fire") to "disk on fire",
            KeyWrapException.WrapFailed() to "wrap failed",
            KeyWrapException.UnwrapFailed() to "unwrap failed",
            KeyWrapException.InvalidKey() to "invalid key",
            KeyWrapException.InvalidKeyLength(expected = 32uL, got = 7uL) to
                "invalid key length: expected 32, got 7",
        )

        for ((thrown, expected) in cases) {
            val result = sessionThrowing(thrown).exportArk()

            assertEquals(SessionError.KeyWrap(expected), (result as Result.Failure).error)
        }
    }

    /** An active session whose every call fails with [thrown], for exercising the error mapping. */
    private fun sessionThrowing(thrown: KeyWrapException) = Session(
        object : ArkSession(NoHandle) {
            override fun isActive(): Boolean = true
            override fun exportArk(): ByteArray = throw ArkSessionException.KeyWrap(thrown)
        },
    )

    @Test
    fun `verifyPassword rejects the wrong password`() = runTest {
        val account = checkNotNull(session.createAccount("hunter2").getOrNull())
        val rewrapped =
            checkNotNull(session.rewrapForNewPassword("hunter2", account.userId).getOrNull())

        val wrong = session.verifyPassword(
            password = "nope",
            salt = rewrapped.salt,
            wrapped = rewrapped.wrapped,
            userId = account.userId,
        )

        assertEquals(SessionError.WrongPassword, (wrong as Result.Failure).error)
    }
}
