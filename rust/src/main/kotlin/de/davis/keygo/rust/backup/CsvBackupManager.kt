package de.davis.keygo.rust.backup

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.CsvImportResult
import de.davisalessandro.keygo.rust.ExportPreset

suspend fun CsvBackupManagerInterface.analyzeWithResult(
    data: String,
): Result<CsvAnalysis, BackupException> = backupResult { analyze(data) }

suspend fun CsvBackupManagerInterface.importWithResult(
    data: String,
    mapping: ColumnMapping,
): Result<CsvImportResult, BackupException> = backupResult { import(data, mapping) }

suspend fun CsvBackupManagerInterface.exportWithResult(
    backup: Backup,
    preset: ExportPreset,
): Result<String, BackupException> = backupResult { export(backup, preset) }
