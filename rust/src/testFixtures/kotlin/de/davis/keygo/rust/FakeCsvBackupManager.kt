package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import de.davisalessandro.keygo.rust.CsvImportResult
import de.davisalessandro.keygo.rust.ExportPreset
import de.davisalessandro.keygo.rust.FieldConfidence
import de.davisalessandro.keygo.rust.ImportReport

class FakeCsvBackupManager : CsvBackupManagerInterface {

    var analyzeResult: CsvAnalysis = CsvAnalysis(
        columns = emptyList(),
        suggested = ColumnMapping(null, null, null, null, null, null),
        confidence = FieldConfidence(null, null, null, null, null, null),
    )
    var importResult: CsvImportResult = CsvImportResult(
        backup = Backup(emptyList()),
        report = ImportReport(imported = 0u, skipped = 0u),
    )
    var exportResult: String = ""
    var analyzeException: BackupException? = null
    var importException: BackupException? = null
    var exportException: BackupException? = null

    override fun analyze(data: String): CsvAnalysis {
        analyzeException?.let { throw it }
        return analyzeResult
    }

    override fun import(data: String, mapping: ColumnMapping): CsvImportResult {
        importException?.let { throw it }
        return importResult
    }

    override fun export(backup: Backup, preset: ExportPreset): String {
        exportException?.let { throw it }
        return exportResult
    }
}
