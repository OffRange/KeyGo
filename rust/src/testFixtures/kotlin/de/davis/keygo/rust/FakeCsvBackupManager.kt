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

    data class AnalyzeCall(val data: String)
    data class ImportCall(val data: String, val mapping: ColumnMapping)
    data class ExportCall(val backup: Backup, val preset: ExportPreset)

    val analyzeCalls = mutableListOf<AnalyzeCall>()
    val importCalls = mutableListOf<ImportCall>()
    val exportCalls = mutableListOf<ExportCall>()

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
        analyzeCalls += AnalyzeCall(data)
        analyzeException?.let { throw it }
        return analyzeResult
    }

    override fun import(data: String, mapping: ColumnMapping): CsvImportResult {
        importCalls += ImportCall(data, mapping)
        importException?.let { throw it }
        return importResult
    }

    override fun export(backup: Backup, preset: ExportPreset): String {
        exportCalls += ExportCall(backup, preset)
        exportException?.let { throw it }
        return exportResult
    }
}
