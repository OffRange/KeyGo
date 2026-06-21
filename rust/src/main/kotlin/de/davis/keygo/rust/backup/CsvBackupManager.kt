package de.davis.keygo.rust.backup

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.CsvImportResult
import de.davisalessandro.keygo.rust.ExportPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias CsvManager = CsvBackupManagerInterface

suspend fun CsvBackupManagerInterface.analyzeWithResult(
    data: String,
): Result<CsvAnalysis, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        analyze(data)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}

suspend fun CsvBackupManagerInterface.importWithResult(
    data: String,
    mapping: ColumnMapping,
): Result<CsvImportResult, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        import(data, mapping)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}

suspend fun CsvBackupManagerInterface.exportWithResult(
    backup: Backup,
    preset: ExportPreset,
): Result<String, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        export(backup, preset)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}
