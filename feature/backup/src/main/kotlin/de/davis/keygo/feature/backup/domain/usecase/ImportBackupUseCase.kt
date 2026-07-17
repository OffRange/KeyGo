package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.BackupRestorer
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportRequest
import de.davis.keygo.rust.backup.analyzeWithResult
import de.davis.keygo.rust.backup.importWithResult
import de.davis.keygo.rust.backup.inspectWithResult
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

@Single
internal class ImportBackupUseCase(
    private val fileStore: BackupFileStore,
    private val jsonBackupManager: JsonBackupManagerInterface,
    private val csvBackupManager: CsvBackupManagerInterface,
    private val restorer: BackupRestorer,
    private val session: Session,
) {

    operator fun invoke(request: ImportRequest): Flow<ImportProgress> = flow {
        if (runCatching { session.ark }.isFailure) {
            emit(ImportProgress.Failed(ImportError.SessionLocked))
            return@flow
        }

        emit(ImportProgress.Reading)
        val text = when (val read = fileStore.read(request.uri)) {
            is Result.Success -> read.success
            is Result.Failure -> {
                emit(ImportProgress.Failed(ImportError.FileUnreadable))
                return@flow
            }
        }
        if (text.isBlank()) {
            emit(ImportProgress.Failed(ImportError.EmptyFile))
            return@flow
        }

        emit(ImportProgress.Parsing)
        val backup = when (val parsed = parse(request, text)) {
            is Result.Success -> parsed.success
            is Result.Failure -> {
                emit(ImportProgress.Failed(parsed.error))
                return@flow
            }
        }

        when (val restored =
            restorer.restore(backup) { p, t -> emit(ImportProgress.Running(p, t)) }) {
            is Result.Success -> emit(ImportProgress.Succeeded(restored.success))
            is Result.Failure -> emit(ImportProgress.Failed(restored.error))
        }
    }

    private suspend fun parse(request: ImportRequest, text: String): Result<Backup, ImportError> =
        resultBinding {
            when (request.format) {
                FileFormat.JSON -> {
                    val credential = when (
                        jsonBackupManager.inspectWithResult(text).bind { it.toImportError() }
                    ) {
                        JsonEncryption.NONE -> null

                        JsonEncryption.PASSPHRASE -> request.passphrase
                            ?.takeIf(String::isNotBlank)
                            ?.let { BackupCredential.Passphrase(it.encodeToByteArray()) }
                            ?: return Result.Failure(ImportError.PassphraseRequired)

                        JsonEncryption.ARK -> BackupCredential.Ark(
                            runCatching { session.ark }.getOrNull()
                                ?: return Result.Failure(ImportError.SessionLocked),
                        )
                    }
                    jsonBackupManager.importWithResult(text, credential).bind { it.toImportError() }
                }

                FileFormat.CSV -> {
                    val analysis = csvBackupManager.analyzeWithResult(text)
                        .bind { it.toImportError() }

                    csvBackupManager.importWithResult(text, analysis.suggested)
                        .bind { it.toImportError() }
                        .backup
                }
            }
        }

    private fun BackupException.toImportError(): ImportError = when (this) {
        is BackupException.Crypto,
        is BackupException.CredentialMismatch,
        is BackupException.MissingCredential,
        is BackupException.UnexpectedCredential,
        is BackupException.EncryptionMismatch -> ImportError.WrongCredential

        else -> ImportError.ParseFailed(this)
    }
}
