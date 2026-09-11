package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.security.domain.toSessionError
import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ArkCredential
import de.davisalessandro.keygo.rust.ArkSessionException
import de.davisalessandro.keygo.rust.ArkSessionInterface
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID

@Single
internal class SessionImpl(
    private val binding: ArkSessionInterface
) : Session {

    private val _isActive = MutableStateFlow(binding.isActive())
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()


    override suspend fun createAccount(password: String): Result<NewAccount, SessionError> =
        derived { binding.createAccount(password) }

    override suspend fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID
    ): Result<Unit, SessionError> =
        derived { binding.unlockWithPassword(password, salt, wrapped, userId) }

    override suspend fun unlockWithArk(arkBytes: ByteArray): Result<Unit, SessionError> =
        catching { binding.unlockWithArk(arkBytes) }.also { syncIsActive() }

    @ExportArk
    override fun exportArk(): Result<ByteArray, SessionError> = catching { binding.exportArk() }

    override fun arkCredential(): ArkCredential = binding.arkCredential()

    override suspend fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID
    ): Result<Unit, SessionError> =
        derived { binding.verifyPassword(password, salt, wrapped, userId) }

    override fun verifyArk(arkBytes: ByteArray): Boolean = binding.verifyArk(arkBytes)

    override suspend fun rewrapForNewPassword(
        newPassword: String,
        userId: UUID
    ): Result<PasswordWrapped, SessionError> =
        derived { binding.rewrapForNewPassword(newPassword, userId) }

    override suspend fun wrapVaultKey(
        vaultKey: ByteArray,
        vaultId: UUID
    ): Result<WrappedKeyBlob, SessionError> = withContext(Dispatchers.Default) {
        catching { binding.wrapVaultKey(vaultKey, vaultId) }
    }

    override suspend fun unwrapVaultKey(
        wrapped: WrappedKeyBlob,
        vaultId: UUID
    ): Result<ByteArray, SessionError> = withContext(Dispatchers.Default) {
        catching { binding.unwrapVaultKey(wrapped, vaultId) }
    }

    override fun endSession() {
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
        _isActive.update { runCatching { binding.isActive() }.getOrDefault(_isActive.value) }
    }

    inline fun <R> catching(block: () -> R): Result<R, SessionError> = try {
        Result.Success(block())
    } catch (e: ArkSessionException) {
        Result.Failure(e.toSessionError())
    }
}