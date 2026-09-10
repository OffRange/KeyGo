package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Custody of the ARK, held in Rust. The key material enters the JVM heap only through [exportArk],
 * [unlockWithArk] and [verifyArk], which exist because the Android Keystore ciphers that seal the
 * biometric copy and the backup escrow only run on this side of the boundary.
 *
 * Every caller of those three wipes its array in a `finally`, and each of those wipes has a test.
 * That covers the copy the caller owns, which is all this code can reach. It is not a claim that no
 * ARK bytes remain in the heap: the biometric paths obtain the key from `javax.crypto`, whose
 * `SecretKey.getEncoded` hands back a fresh copy and keeps its own, and a moving GC may have copied
 * any of them. Rust custody is what makes the ARK's *resident* lifetime bounded; the JVM-side wipes
 * shorten the window at these three doors rather than closing it.
 *
 * [binding] is the generated UniFFI object. Passing it on is how backup hands the session across the
 * FFI; it grants no access this class does not already expose.
 */
class Session(val binding: ArkSession) {

    private val _isActive = MutableStateFlow(binding.isActive())

    /** Observable lock state, for callers that react to a session ending rather than read it. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    suspend fun createAccount(password: String): Result<NewAccount, SessionError> =
        derived { binding.createAccount(password) }

    suspend fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError> =
        derived { binding.unlockWithPassword(password, salt, wrapped, userId) }

    /** Takes custody of an ARK recovered from the Keystore. The caller still owns [arkBytes]. */
    fun unlockWithArk(arkBytes: ByteArray): Result<Unit, SessionError> =
        catching { binding.unlockWithArk(arkBytes) }.also { syncIsActive() }

    /** The caller owns the returned array and must wipe it once the Keystore has sealed it. */
    fun exportArk(): Result<ByteArray, SessionError> = catching { binding.exportArk() }

    suspend fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError> =
        derived { binding.verifyPassword(password, salt, wrapped, userId) }

    fun verifyArk(arkBytes: ByteArray): Boolean = binding.verifyArk(arkBytes)

    suspend fun rewrapForNewPassword(
        newPassword: String,
        userId: UUID,
    ): Result<PasswordWrapped, SessionError> =
        derived { binding.rewrapForNewPassword(newPassword, userId) }

    suspend fun wrapVaultKey(
        vaultKey: ByteArray,
        vaultId: UUID,
    ): Result<WrappedKeyBlob, SessionError> = catching { binding.wrapVaultKey(vaultKey, vaultId) }

    suspend fun unwrapVaultKey(
        wrapped: WrappedKeyBlob,
        vaultId: UUID,
    ): Result<ByteArray, SessionError> = catching { binding.unwrapVaultKey(wrapped, vaultId) }

    fun endSession() {
        binding.end()
        syncIsActive()
    }

    /**
     * Runs off the main thread: everything in here reaches Argon2. Re-publishes [isActive] in a
     * `finally` inside the dispatched block rather than after it. `binding.createAccount` and
     * `binding.unlockWithPassword` are blocking JNI calls that run to completion regardless of
     * cancellation, so if the caller's coroutine is cancelled while this suspends, `withContext`
     * throws on resumption instead of returning. A sync placed after the `withContext` call would
     * never run, leaving [isActive] stale while Rust already holds (or released) the ARK.
     */
    private suspend fun <R> derived(block: () -> R): Result<R, SessionError> =
        withContext(Dispatchers.Default) {
            try {
                catching(block)
            } finally {
                syncIsActive()
            }
        }

    /**
     * Republishes the lock state, swallowing anything [ArkSession.isActive] throws.
     * It can throw on a destroyed handle, and this runs in a `finally`: an exception raised here
     * would replace a perfectly good return value, or discard an in-flight exception on its way
     * out. Losing one lock-state update is the smaller failure, and the next call republishes it.
     */
    private fun syncIsActive() {
        _isActive.value = runCatching { binding.isActive() }.getOrDefault(_isActive.value)
    }

    /** Only [ArkSessionException] is an expected failure; anything else is a bug and propagates. */
    private fun <R> catching(block: () -> R): Result<R, SessionError> = try {
        Result.Success(block())
    } catch (e: ArkSessionException) {
        Result.Failure(e.toSessionError())
    }
}

private fun ArkSessionException.toSessionError(): SessionError = when (this) {
    is ArkSessionException.Locked -> SessionError.Locked
    is ArkSessionException.WrongPassword -> SessionError.WrongPassword
    is ArkSessionException.Derivation -> SessionError.Derivation(v1)
    is ArkSessionException.KeyWrap -> SessionError.KeyWrap(v1.describe())
}

/**
 * A message for each [KeyWrapException] variant, read from its own fields rather than its
 * generated `message`: that getter prefixes [KeyWrapException.Other] with `"v1="`, and `Other` is
 * production-reachable (the catch-all arm of `From<CryptoError> for KeyWrapError` on the Rust
 * side), so that prefix could otherwise leak into a real [SessionError.KeyWrap] payload.
 */
private fun KeyWrapException.describe(): String = when (this) {
    is KeyWrapException.WrapFailed -> "wrap failed"
    is KeyWrapException.UnwrapFailed -> "unwrap failed"
    is KeyWrapException.InvalidKey -> "invalid key"
    is KeyWrapException.InvalidKeyLength -> "invalid key length: expected $expected, got $got"
    is KeyWrapException.Other -> v1
}
