package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.PersistableUriManager
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FinishExportWizardError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class FinishExportWizardUseCase(
    private val backupScheduler: BackupScheduler,
    private val keyStoreManager: KeyStoreManager,
    private val persistableUriManager: PersistableUriManager,
) {

    suspend operator fun invoke(details: ExportDetails): Result<Unit, FinishExportWizardError> =
        resultBinding {
            if (details.format.encrypted && details.passphrase.isBlank())
                return Result.Failure(FinishExportWizardError.PassphraseEmpty)

            val passphrase = if (details.format.encrypted)
                wrapPassphrase(details.passphrase).bind()
            else null

            val job = BackupJob(
                uri = details.uri,
                passphrase = passphrase,
                format = details.format,
            )

            when (val interval = details.interval) {
                null -> backupScheduler.scheduleOneTimeBackup(job)
                    .bind { FinishExportWizardError.SchedulePersistenceFailed }

                else -> {
                    persistableUriManager.takePersistableUriPermission(details.uri)
                    backupScheduler.scheduleRecurringBackup(job, interval)
                        .bind { FinishExportWizardError.SchedulePersistenceFailed }
                }
            }

            return Result.Success(Unit)
        }

    private suspend fun wrapPassphrase(
        passphrase: String,
    ): Result<CryptographicData, FinishExportWizardError> = withContext(Dispatchers.Default) {
        val cipher = keyStoreManager.getOrCreateCipherFor(
            keyId = KeyId.BackupPassphraseKey,
            cryptographicMode = CryptographicMode.Encrypt,
        )

        runCatching {
            CryptographicData(
                data = cipher.doFinal(passphrase.encodeToByteArray()),
                iv = cipher.iv,
            )
        }.getOrNull().asResult(FinishExportWizardError.CryptoFailed)
    }
}
