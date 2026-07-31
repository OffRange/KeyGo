package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonEncryption

class FakeJsonBackupManager : JsonBackupManagerInterface {

    data class ExportCall(val backup: Backup, val credential: BackupCredential)
    data class ImportCall(val data: String, val credential: BackupCredential)

    val exportCalls = mutableListOf<ExportCall>()
    val importCalls = mutableListOf<ImportCall>()
    val inspectCalls = mutableListOf<String>()

    var exportResult: String = "{}"
    var importResult: Backup = Backup(emptyList())
    var inspectResult: JsonEncryption = JsonEncryption.PASSPHRASE
    var exportException: BackupException? = null
    var importException: BackupException? = null
    var inspectException: BackupException? = null

    /**
     * Receives the live credential rather than the recorded snapshot, so a test can hold the very
     * array the caller passed in and assert it was zeroed after the call returned.
     */
    var onImport: ((data: String, credential: BackupCredential) -> Unit)? = null

    override fun export(backup: Backup, credential: BackupCredential): String {
        exportCalls += ExportCall(backup, credential.snapshot())
        exportException?.let { throw it }
        return exportResult
    }

    override fun import(data: String, credential: BackupCredential): Backup {
        onImport?.invoke(data, credential)
        importCalls += ImportCall(data, credential.snapshot())
        importException?.let { throw it }
        return importResult
    }

    // Callers zero secret key material as soon as the call returns (a recovered ARK, a decrypted
    // passphrase), so record the bytes we were called with rather than a live reference to them.
    private fun BackupCredential.snapshot(): BackupCredential = when (this) {
        is BackupCredential.Ark -> BackupCredential.Ark(key.copyOf())
        is BackupCredential.Passphrase -> BackupCredential.Passphrase(bytes.copyOf())
    }

    override fun inspect(data: String): JsonEncryption {
        inspectCalls += data
        inspectException?.let { throw it }
        return inspectResult
    }
}
