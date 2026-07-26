package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProviderFactory
import de.davis.keygo.core.security.domain.crypto.suspendDoFinal
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.data.BackupSession
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import org.koin.core.annotation.Single

/**
 * Resolves the crypto scope for a backup. Prefers the live [Session]; when locked, silently recovers
 * the ARK copy via the non-auth [KeyId.BackupArkKey] and binds the scope to a throwaway
 * [BackupSession]. The global session is never touched.
 */
@Single
internal class BackupArkUnlocker(
    private val session: Session,
    private val keyStoreManager: KeyStoreManager,
    private val arkKeyStore: BackupArkKeyStore,
    private val scopeProviderFactory: CryptographicScopeProviderFactory,
    private val vaultRepository: VaultRepository,
) {

    /**
     * Runs [block] with the ARK for this backup. A recovered ARK is zeroed afterwards; a live
     * [Session.ark] is the app's own session key and is left alone.
     */
    suspend fun <R> withArk(block: suspend (ByteArray) -> R): Result<R, ExportError> {
        val live = session.arkOrNull()
        if (live != null) return Result.Success(block(live))

        return resultBinding {
            val ark = recoverArk().bind()
            try {
                block(ark)
            } finally {
                ark.fill(0)
            }
        }
    }

    /** Runs [block] with a crypto scope bound to the live session, or to a throwaway
     * [BackupSession] holding a recovered ARK that is zeroed afterwards. */
    suspend fun <R> withScope(
        block: suspend (ItemWithCryptoScopeUseCase) -> R,
    ): Result<R, ExportError> {
        val live = session.arkOrNull()
        if (live != null) return Result.Success(block(scopeFor(session)))

        return resultBinding {
            val ark = recoverArk().bind()
            try {
                block(scopeFor(BackupSession(ark)))
            } finally {
                ark.fill(0)
            }
        }
    }

    private suspend fun recoverArk(): Result<ByteArray, ExportError> = resultBinding {
        val wrapped = arkKeyStore.load()
            .asResult(ExportError.NotProvisioned).bind()

        val cipher = runCatching {
            keyStoreManager.getOrCreateCipherFor(
                keyId = KeyId.BackupArkKey,
                cryptographicMode = CryptographicMode.Decrypt,
                iv = wrapped.iv,
            )
        }.getOrNull().asResult(ExportError.DeviceLocked).bind()

        cipher.suspendDoFinal(wrapped.data).bind { ExportError.DeviceLocked }
    }

    private fun scopeFor(session: Session): ItemWithCryptoScopeUseCase =
        ItemWithCryptoScopeUseCase(vaultRepository, scopeProviderFactory.forSession(session))
}
