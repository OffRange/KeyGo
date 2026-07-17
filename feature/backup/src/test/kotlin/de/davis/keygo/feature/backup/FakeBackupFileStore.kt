package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupEntry

class FakeBackupFileStore : BackupFileStore {

    var contents: String? = null
    var readError: Throwable? = null
    var writeError: Throwable? = null
    var listError: Throwable? = null
    var deleteError: Throwable? = null

    // Details of the most recent writeNewDocument call.
    var writtenText: String? = null
    var writtenFileName: String? = null
    var writtenMimeType: String? = null
    var writtenFolder: BackupDestinationUri? = null

    // Documents currently present in the folder; writeNewDocument adds, delete removes.
    val backups = mutableListOf<BackupEntry>()
    val deleted = mutableListOf<BackupDestinationUri>()

    override suspend fun read(uri: BackupDestinationUri): Result<String, Throwable> {
        readError?.let { return Result.Failure(it) }
        return contents?.let { Result.Success(it) }
            ?: Result.Failure(IllegalStateException("no contents"))
    }

    override suspend fun writeNewDocument(
        folder: BackupDestinationUri,
        fileName: String,
        mimeType: String,
        text: String,
    ): Result<Unit, Throwable> {
        writeError?.let { return Result.Failure(it) }
        writtenFolder = folder
        writtenFileName = fileName
        writtenMimeType = mimeType
        writtenText = text
        backups += BackupEntry(BackupDestinationUri("${folder.value}/$fileName"), fileName)
        return Result.Success(Unit)
    }

    override suspend fun listBackups(
        folder: BackupDestinationUri,
        baseName: String,
    ): Result<List<BackupEntry>, Throwable> {
        listError?.let { return Result.Failure(it) }
        return Result.Success(backups.filter { it.name.startsWith(baseName) })
    }

    override suspend fun delete(uri: BackupDestinationUri): Result<Unit, Throwable> {
        deleteError?.let { return Result.Failure(it) }
        deleted += uri
        backups.removeAll { it.uri == uri }
        return Result.Success(Unit)
    }
}
