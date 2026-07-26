package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.suspendDoFinal
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.PersistableUriManager
import de.davis.keygo.feature.backup.domain.arkOrNull
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FinishExportWizardError
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
class FinishExportWizardUseCase(
    private val backupScheduler: BackupScheduler,
    private val keyStoreManager: KeyStoreManager,
    private val persistableUriManager: PersistableUriManager,
    private val session: Session,
    private val arkKeyStore: BackupArkKeyStore,
    private val provisioningLock: BackupProvisioningLock,
) {

    suspend operator fun invoke(details: ExportDetails): Result<Unit, FinishExportWizardError> =
        resultBinding {
            // Hold the lock across the whole provision-and-schedule section: a concurrent cleanup
            // must never observe a half-provisioned job (escrow written, record not yet) and
            // destroy the credentials this job needs.
            provisioningLock.mutex.withLock {
                // encryption == null on an encrypted format fails closed to the passphrase path.
                val passphraseRequired =
                    details.format.encrypted && details.encryption != EncryptionMethod.Ark
                if (passphraseRequired && details.passphrase.isBlank())
                    return Result.Failure(FinishExportWizardError.PassphraseEmpty)

                val wrappedPassphrase = if (passphraseRequired)
                    wrapPassphrase(details.passphrase).bind()
                else null

                val job = BackupJob(
                    uri = details.uri,
                    wrappedPassphrase = wrappedPassphrase,
                    format = details.format,
                    encryption = details.encryption,
                    csvPreset = details.csvPreset,
                    keepCount = details.keepCount,
                )

                // The worker may run long after the wizard closes (and across reboots), so hold on
                // to folder access for both one-time and recurring backups.
                runCatching { persistableUriManager.takePersistableUriPermission(details.uri) }
                    .getOrNull()
                    .asResult(FinishExportWizardError.DestinationPermissionDenied)
                    .bind()

                provisionBackupArk().bind()

                when (val interval = details.interval) {
                    null -> backupScheduler.scheduleOneTimeBackup(job)
                    else -> backupScheduler.scheduleRecurringBackup(job, interval)
                }
                    // No record was written to drive this grant's release, and the escrow/aliases
                    // only self-heal via a later cleanup - but the persistable URI grant would leak
                    // against the platform cap. Best-effort release it while unwinding the failure.
                    .onFailure {
                        runCatching {
                            persistableUriManager.releasePersistableUriPermission(details.uri)
                        }
                    }
                    .bind { FinishExportWizardError.SchedulePersistenceFailed }

                return Result.Success(Unit)
            }
        }

    private suspend fun wrapPassphrase(
        passphrase: String,
    ): Result<CryptographicData, FinishExportWizardError> = resultBinding {
        val cipher = keyStoreManager.getOrCreateCipherFor(
            keyId = KeyId.BackupPassphraseKey,
            cryptographicMode = CryptographicMode.Encrypt,
        )

        CryptographicData(
            data = cipher.suspendDoFinal(passphrase.encodeToByteArray())
                .bind { FinishExportWizardError.CryptoFailed },
            iv = cipher.iv,
        )
    }

    private suspend fun provisionBackupArk() = resultBinding {
        val ark = session.arkOrNull()
            .asResult(FinishExportWizardError.CryptoFailed).bind()

        val cipher = keyStoreManager.getOrCreateCipherFor(
            keyId = KeyId.BackupArkKey,
            cryptographicMode = CryptographicMode.Encrypt,
        )

        val data = cipher.suspendDoFinal(ark).bind { FinishExportWizardError.CryptoFailed }
        arkKeyStore.save(CryptographicData(data, cipher.iv))
    }
}
