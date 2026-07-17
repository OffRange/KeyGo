package de.davis.keygo.rust.backup

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.JsonBackupManagerInterface
import de.davisalessandro.keygo.rust.JsonEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias JsonBackupManager = JsonBackupManagerInterface

suspend fun JsonBackupManagerInterface.exportWithResult(
    backup: Backup,
    credential: BackupCredential?,
): Result<String, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        export(backup, credential)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}

suspend fun JsonBackupManagerInterface.importWithResult(
    data: String,
    credential: BackupCredential?,
): Result<Backup, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        import(data, credential)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}

suspend fun JsonBackupManagerInterface.inspectWithResult(
    data: String,
): Result<JsonEncryption, BackupException> = withContext(Dispatchers.Default) {
    runCatching {
        inspect(data)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) }
    )
}
