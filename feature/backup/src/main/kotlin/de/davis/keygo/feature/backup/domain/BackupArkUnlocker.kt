package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionFactory
import de.davis.keygo.core.security.domain.crypto.suspendDoFinal
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import org.koin.core.annotation.Single

/**
 * Resolves the session a backup runs under. Prefers the live [Session]; when locked, silently
 * recovers the ARK copy via the non-auth [KeyId.BackupArkKey] and hands it to a throwaway session
 * from [SessionFactory].
 *
 * The app-wide session is never touched. The escrowed ARK is readable with no user present, so
 * unlocking the app-wide session with it would open the whole app, and every feature reading that
 * session, for as long as the backup ran. Ending it afterwards would also end a session the user
 * unlocked in the meantime.
 */
@Single
internal class BackupArkUnlocker(
    private val session: Session,
    private val sessionFactory: SessionFactory,
    private val keyStoreManager: KeyStoreManager,
    private val arkKeyStore: BackupArkKeyStore,
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
                val recovered = sessionFactory.create()
                try {
                    // Nothing else can lock a session this fresh, so a rejection is the escrowed
                    // bytes themselves. No retry can fix that; it would only hold the escrow open.
                    recovered.unlockWithArk(ark).bind { ExportError.CryptoFailed }
                    block(recovered)
                } finally {
                    recovered.endSession()
                }
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
}
