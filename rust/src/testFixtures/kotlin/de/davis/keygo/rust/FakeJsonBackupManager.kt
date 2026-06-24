package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface

class FakeJsonBackupManager : JsonBackupManagerInterface {

    data class ExportCall(val backup: Backup, val credential: BackupCredential?)
    data class ImportCall(val data: String, val credential: BackupCredential?)

    val exportCalls = mutableListOf<ExportCall>()
    val importCalls = mutableListOf<ImportCall>()

    var exportResult: String = "{}"
    var importResult: Backup = Backup(emptyList())
    var exportException: BackupException? = null
    var importException: BackupException? = null

    override fun export(backup: Backup, credential: BackupCredential?): String {
        exportCalls += ExportCall(backup, credential)
        exportException?.let { throw it }
        return exportResult
    }

    override fun import(data: String, credential: BackupCredential?): Backup {
        importCalls += ImportCall(data, credential)
        importException?.let { throw it }
        return importResult
    }
}
