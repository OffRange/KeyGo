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
import de.davis.keygo.feature.backup.domain.model.BackupSchedule
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FinishExportWizardError
import de.davis.keygo.feature.backup.domain.repository.BackupScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class FinishExportWizardUseCase(
    private val backupScheduler: BackupScheduler,
    private val keyStoreManager: KeyStoreManager,
    private val persistableUriManager: PersistableUriManager,
    private val backupScheduleRepository: BackupScheduleRepository,
) {

    suspend operator fun invoke(details: ExportDetails): Result<Unit, FinishExportWizardError> =
        resultBinding {
            if (details.format.encrypted && details.passphrase.isBlank())
                return Result.Failure(FinishExportWizardError.PassphraseEmpty)

            when (val interval = details.interval) {
                null -> backupScheduler.scheduleOneTimeBackup()
                else -> {
                    setupRecurringSchedule(details).bind()
                    persistableUriManager.takePersistableUriPermission(details.uri)
                    backupScheduler.scheduleRecurringBackup(interval)
                }
            }

            return Result.Success(Unit)
        }

    private suspend fun setupRecurringSchedule(details: ExportDetails) = resultBinding {
        val encrypted = withContext(Dispatchers.Default) {
            val cipher = keyStoreManager.getOrCreateCipherFor(
                keyId = KeyId.BackupPassphraseKey,
                cryptographicMode = CryptographicMode.Encrypt,
            )

            runCatching {
                CryptographicData(
                    data = cipher.doFinal(details.passphrase.encodeToByteArray()),
                    iv = cipher.iv
                )
            }.getOrNull().asResult(FinishExportWizardError.CryptoFailed).bind()
        }

        backupScheduleRepository.setSchedule(
            BackupSchedule(
                uri = details.uri,
                passphrase = encrypted,
                format = details.format,
            )
        ).bind { FinishExportWizardError.SchedulePersistenceFailed }
    }
}
