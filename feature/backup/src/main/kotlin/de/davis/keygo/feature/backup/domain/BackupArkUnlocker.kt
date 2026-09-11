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
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import org.koin.core.annotation.Single

/**
 * Resolves the session a backup runs under. Prefers the live [Session] when it is already
 * unlocked; otherwise silently recovers the ARK copy via the non-auth [KeyId.BackupArkKey] and
 * unlocks the same injected [Session] with it, ending it again once the block returns.
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
     * Runs [block] with a session holding the ARK for this backup: the live one when unlocked,
     * otherwise a throwaway holding a copy recovered from escrow. The throwaway is ended and the
     * recovered bytes are zeroed afterwards; a live session is left alone, since its ARK is the
     * app's own key.
     */
    suspend fun <R> withSession(block: suspend (Session) -> R): Result<R, ExportError> {
        if (session.isActive.value) return Result.Success(block(session))

        return resultBinding {
            val ark = recoverArk().bind()
            try {
                // Creating the session sits inside the wipe guard: it can throw, and the recovered
                // ARK is already in hand by then. Ending it has its own guard, so a session is
                // never left holding a key because the block below failed.
                try {
                    session.unlockWithArk(ark).bind { ExportError.DeviceLocked }
                    block(session)
                } finally {
                    session.endSession()
                }
            } finally {
                ark.fill(0)
            }
        }
    }

    /** Runs [block] with a crypto scope bound to whichever session [withSession] resolves. */
    suspend fun <R> withScope(
        block: suspend (ItemWithCryptoScopeUseCase) -> R,
    ): Result<R, ExportError> = withSession { block(scopeFor(it)) }

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
