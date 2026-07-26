package de.davis.keygo.rust.backup

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonEncryption

suspend fun JsonBackupManagerInterface.exportWithResult(
    backup: Backup,
    credential: BackupCredential?,
): Result<String, BackupException> = backupResult { export(backup, credential) }

suspend fun JsonBackupManagerInterface.importWithResult(
    data: String,
    credential: BackupCredential?,
): Result<Backup, BackupException> = backupResult { import(data, credential) }

suspend fun JsonBackupManagerInterface.inspectWithResult(
    data: String,
): Result<JsonEncryption, BackupException> = backupResult { inspect(data) }
