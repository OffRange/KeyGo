package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.ArkSessionInterface
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

interface LegacySession {

    /** Observable lock state, for callers that have to react to a session ending rather than read it. */
    val isActive: StateFlow<Boolean>

    /**
     * Runs [block] with the live ARK, or returns `null` without running it when locked. Null is the
     * ordinary locked branch every caller handles.
     *
     * The ARK is wiped in place when a session ends, so the array is only valid inside [block] -
     * copy what has to outlive it. The bytes stay intact for the whole of [block] however long it
     * suspends, even if the session ends underneath.
     */
    suspend fun <R> withArk(block: suspend (ByteArray) -> R): R?

    fun startSession(ark: ByteArray)
    fun endSession()
}

/** [LegacySession.withArk] for callers in [Result]: a locked session becomes [locked], not a null. */
suspend fun <R, E> LegacySession.withArkOr(
    locked: E,
    block: suspend (ByteArray) -> Result<R, E>,
): Result<R, E> = withArk(block) ?: Result.Failure(locked)

/**
 * Custody of the ARK, held in Rust. The key material never enters the JVM heap except through
 * [exportArk] and [unlockWithArk], which exist because the Android Keystore ciphers that seal the
 * biometric copy and the backup escrow only run on this side of the boundary.
 *
 * [binding] is the generated UniFFI object. Passing it on is how backup hands the session across the
 * FFI; it grants no access this class does not already expose.
 */
class Session(val binding: ArkSessionInterface) {

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
        catching { binding.unlockWithArk(arkBytes) }.also { _isActive.value = binding.isActive() }

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
        _isActive.value = binding.isActive()
    }

    /**
     * Runs off the main thread: everything in here reaches Argon2. Re-publishes [isActive] in a
     * `finally` inside the dispatched block, not after it: `binding.createAccount` and
     * `binding.unlockWithPassword` are blocking JNI calls that run to completion regardless of
     * cancellation, so if the caller's coroutine is cancelled while this suspends, `withContext`
     * throws on resumption instead of returning - a sync placed after the `withContext` call would
     * never run, leaving [isActive] stale while Rust already holds (or released) the ARK.
     */
    private suspend fun <R> derived(block: () -> R): Result<R, SessionError> =
        withContext(Dispatchers.Default) {
            try {
                catching(block)
            } finally {
                _isActive.value = binding.isActive()
            }
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
