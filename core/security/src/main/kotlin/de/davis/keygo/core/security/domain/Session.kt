package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ArkCredential
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

class Session(@PublishedApi internal val binding: ArkSession) {

    private val _isActive = MutableStateFlow(binding.isActive())

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

    fun unlockWithArk(arkBytes: ByteArray): Result<Unit, SessionError> =
        catching { binding.unlockWithArk(arkBytes) }.also { syncIsActive() }

    inline fun <T> useArk(block: (ByteArray) -> T): Result<T, SessionError> = catching {
        val ark = binding.exportArk()
        try {
            block(ark)
        } finally {
            ark.fill(0)
        }
    }

    fun arkCredential(): ArkCredential = binding.arkCredential()

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
    ): Result<WrappedKeyBlob, SessionError> = withContext(Dispatchers.Default) {
        catching { binding.wrapVaultKey(vaultKey, vaultId) }
    }

    suspend fun unwrapVaultKey(
        wrapped: WrappedKeyBlob,
        vaultId: UUID,
    ): Result<ByteArray, SessionError> = withContext(Dispatchers.Default) {
        catching { binding.unwrapVaultKey(wrapped, vaultId) }
    }

    fun endSession() {
        binding.end()
        syncIsActive()
    }

    private suspend fun <R> derived(block: () -> R): Result<R, SessionError> =
        withContext(Dispatchers.Default) {
            try {
                catching(block)
            } finally {
                syncIsActive()
            }
        }

    private fun syncIsActive() {
        _isActive.value = runCatching { binding.isActive() }.getOrDefault(_isActive.value)
    }

    @PublishedApi
    internal inline fun <R> catching(block: () -> R): Result<R, SessionError> = try {
        Result.Success(block())
    } catch (e: ArkSessionException) {
        Result.Failure(e.toSessionError())
    }
}

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
