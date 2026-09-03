package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.withArkOr
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.BackupRestorer
import de.davis.keygo.feature.backup.domain.mapper.toImportError
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportRequest
import de.davis.keygo.rust.backup.analyzeWithResult
import de.davis.keygo.rust.backup.importWithResult
import de.davis.keygo.rust.backup.inspectWithResult
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.koin.core.annotation.Single

@Single
internal class ImportBackupUseCase(
    private val fileStore: BackupFileStore,
    private val jsonBackupManager: JsonBackupManagerInterface,
    private val csvBackupManager: CsvBackupManagerInterface,
    private val restorer: BackupRestorer,
    private val session: Session,
) {

    /**
     * [channelFlow], not [kotlinx.coroutines.flow.flow]: [BackupRestorer] reports progress from
     * inside a Room transaction, which runs the block in its own coroutine. `flow` forbids emitting
     * across a coroutine boundary, so the progress ticks must go through a channel.
     */
    operator fun invoke(request: ImportRequest): Flow<ImportProgress> = channelFlow {
        val outcome = resultBinding {
            if (!session.isActive.value)
                Result.Failure<Nothing, ImportError>(ImportError.SessionLocked).bind()

            send(ImportProgress.Reading)
            val text = fileStore.read(request.uri).bind { ImportError.FileUnreadable }
            if (text.isBlank())
                Result.Failure<Nothing, ImportError>(ImportError.EmptyFile).bind()

            send(ImportProgress.Parsing)
            val backup = parse(request, text).bind()

            restorer.restore(backup, request.target) { p, t ->
                send(ImportProgress.Running(p, t))
            }.bind()
        }

        send(
            outcome.fold(
                onSuccess = { ImportProgress.Succeeded(it) },
                onFailure = { ImportProgress.Failed(it) },
            ),
        )
    }

    private suspend fun parse(request: ImportRequest, text: String): Result<Backup, ImportError> =
        resultBinding {
            when (request.format) {
                FileFormat.JSON -> when (
                    jsonBackupManager.inspectWithResult(text).bind { it.toImportError() }
                ) {
                    JsonEncryption.PASSPHRASE -> {
                        val passphrase = request.passphrase
                            ?.takeIf(String::isNotBlank)
                            ?: return Result.Failure(ImportError.PassphraseRequired)
                        val credential =
                            BackupCredential.Passphrase(passphrase.encodeToByteArray())
                        // Zero the derived bytes once Rust is done, mirroring the export path.
                        try {
                            importJson(text, credential).bind()
                        } finally {
                            credential.bytes.fill(0)
                        }
                    }

                    JsonEncryption.ARK -> session.withArkOr(ImportError.SessionLocked) { ark ->
                        importJson(text, BackupCredential.Ark(ark))
                    }.bind()
                }

                FileFormat.CSV -> {
                    val mapping = request.csvMapping ?: csvBackupManager.analyzeWithResult(text)
                        .bind { it.toImportError() }.suggested

                    csvBackupManager.importWithResult(text, mapping)
                        .bind { it.toImportError() }
                        .backup
                }
            }
        }

    private suspend fun importJson(
        text: String,
        credential: BackupCredential,
    ): Result<Backup, ImportError> = jsonBackupManager.importWithResult(text, credential)
        .mapFailure { it.toImportError() }
}
