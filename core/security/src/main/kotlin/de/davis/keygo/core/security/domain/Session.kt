package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.resultBinding
import de.davisalessandro.keygo.rust.ArkCredential
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

@RequiresOptIn("This API must be used with caution! Callers should wipe the returned ARK. Call `useArk` instead to ensure that the ARK is zeroed after use.")
@Retention(AnnotationRetention.BINARY)
annotation class ExportArk

interface Session {

    val isActive: StateFlow<Boolean>

    suspend fun createAccount(password: String): Result<NewAccount, SessionError>

    suspend fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError>

    suspend fun unlockWithArk(arkBytes: ByteArray): Result<Unit, SessionError>

    @ExportArk
    fun exportArk(): Result<ByteArray, SessionError>

    fun arkCredential(): ArkCredential

    suspend fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError>

    fun verifyArk(arkBytes: ByteArray): Boolean

    suspend fun rewrapForNewPassword(
        newPassword: String,
        userId: UUID,
    ): Result<PasswordWrapped, SessionError>

    suspend fun wrapVaultKey(
        vaultKey: ByteArray,
        vaultId: UUID,
    ): Result<WrappedKeyBlob, SessionError>

    suspend fun unwrapVaultKey(
        wrapped: WrappedKeyBlob,
        vaultId: UUID,
    ): Result<ByteArray, SessionError>

    fun endSession()
}

/**
 * Deliberately plain control flow, no [resultBinding]: [block] is caller-supplied and often binds
 * its own, unrelated error type. Using [resultBinding] here would let a caller's `.bind()` - even
 * though it resolves correctly to their own outer scope - throw through this function's own catch
 * on its way out, matching the wrong error type. [fold] can't make that mistake: there is no shared
 * exception type to catch.
 */
@OptIn(ExportArk::class)
inline fun <T> Session.useArk(block: (ByteArray) -> T): Result<T, SessionError> =
    exportArk().fold(
        onSuccess = { ark ->
            try {
                Result.Success(block(ark))
            } finally {
                ark.fill(0)
            }
        },
        onFailure = { Result.Failure(it) },
    )

@PublishedApi
internal fun ArkSessionException.toSessionError(): SessionError = when (this) {
    is ArkSessionException.Locked -> SessionError.Locked
    is ArkSessionException.WrongPassword -> SessionError.WrongPassword
    is ArkSessionException.Derivation -> SessionError.Derivation(v1)
    is ArkSessionException.KeyWrap -> SessionError.KeyWrap(v1.describe())
}

private fun KeyWrapException.describe(): String = when (this) {
    is KeyWrapException.WrapFailed -> "wrap failed"
    is KeyWrapException.UnwrapFailed -> "unwrap failed"
    is KeyWrapException.InvalidKey -> "invalid key"
    is KeyWrapException.InvalidKeyLength -> "invalid key length: expected $expected, got $got"
    is KeyWrapException.Other -> v1
}
