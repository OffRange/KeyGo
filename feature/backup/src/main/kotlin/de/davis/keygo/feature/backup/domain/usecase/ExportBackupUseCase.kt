package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.crypto.suspendDoFinal
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.ResultBinding
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupArkUnlocker
import de.davis.keygo.feature.backup.domain.BackupCollector
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.mapper.toRust
import de.davis.keygo.feature.backup.domain.model.BACKUP_BASE_NAME
import de.davis.keygo.feature.backup.domain.model.BackupEntry
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.backupFileName
import de.davis.keygo.rust.backup.exportWithResult
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

@Single
internal class ExportBackupUseCase(
    private val collector: BackupCollector,
    private val fileStore: BackupFileStore,
    private val jsonBackupManager: JsonBackupManagerInterface,
    private val csvBackupManager: CsvBackupManagerInterface,
    private val keyStoreManager: KeyStoreManager,
    private val arkUnlocker: BackupArkUnlocker,
) {

    operator fun invoke(job: BackupJob): Flow<ExportProgress> = flow {
        resultBinding {
            val collected = collector.collect { p, t -> emit(ExportProgress.Running(p, t)) }.bind()

            emit(ExportProgress.Writing)

            val serialized = serialize(job, collected.backup).bind()

            val fileName = job.format.backupFileName(System.currentTimeMillis())

            fileStore.writeNewDocument(job.uri, fileName, job.format.mimeType, serialized)
                .bind { ExportError.WriteFailed }

            collected.itemCount
        }.onSuccess { count ->
            prune(job)
            emit(ExportProgress.Succeeded(count))
        }.onFailure { failure ->
            emit(ExportProgress.Failed(failure))
        }
    }

    // Best-effort: keep the newest [BackupJob.keepCount] documents of this format in the folder.
    // Failures here never fail the backup itself - the new file is already written.
    private suspend fun prune(job: BackupJob) {
        val keep = job.keepCount ?: return
        val existing = fileStore.listBackups(job.uri, BACKUP_BASE_NAME).getOrNull() ?: return
        existing
            .filter { it.name.endsWith(".${job.format.extension}") }
            .sortedByDescending { it.timestamp(job.format) }
            .drop(keep)
            .forEach { fileStore.delete(it.uri) }
    }

    private fun BackupEntry.timestamp(format: FileFormat): Long =
        name.removePrefix("$BACKUP_BASE_NAME-")
            .removeSuffix(".${format.extension}")
            .toLongOrNull() ?: Long.MIN_VALUE

    private suspend fun serialize(job: BackupJob, backup: Backup): Result<String, ExportError> =
        resultBinding {
            when (job.format) {
                FileFormat.JSON -> when (job.encryption) {
                    EncryptionMethod.Ark -> arkUnlocker.withArk { ark ->
                        jsonBackupManager.exportWithResult(backup, BackupCredential.Ark(ark))
                            .bindToSerializationFailed()
                    }.bind()

                    // null on a persisted pre-field job means passphrase (see mapper).
                    EncryptionMethod.Passphrase, null -> {
                        val passphrase = decryptPassphrase(job).bind()
                        try {
                            jsonBackupManager
                                .exportWithResult(backup, BackupCredential.Passphrase(passphrase))
                                .bindToSerializationFailed()
                        } finally {
                            passphrase.fill(0)
                        }
                    }
                }

                FileFormat.CSV -> csvBackupManager.exportWithResult(
                    backup,
                    (job.csvPreset ?: CsvPreset.Browser).toRust(),
                ).bindToSerializationFailed()
            }
        }

    context(binder: ResultBinding<ExportError>)
    private fun Result<String, BackupException>.bindToSerializationFailed(): String =
        with(binder) { bind { ExportError.SerializationFailed(it) } }

    private suspend fun decryptPassphrase(job: BackupJob): Result<ByteArray, ExportError> =
        resultBinding {
            val wrapped = job.wrappedPassphrase
                ?: return Result.Failure(ExportError.CryptoFailed)

            val cipher = runCatching {
                keyStoreManager.getOrCreateCipherFor(
                    keyId = KeyId.BackupPassphraseKey,
                    cryptographicMode = CryptographicMode.Decrypt,
                    iv = wrapped.iv,
                )
            }.getOrNull().asResult(ExportError.DeviceLocked).bind()

            cipher.suspendDoFinal(wrapped.data).bind { ExportError.CryptoFailed }
        }
}
