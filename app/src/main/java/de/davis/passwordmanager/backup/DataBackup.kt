package de.davis.passwordmanager.backup

import de.davis.passwordmanager.backup.listener.BackupListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

abstract class DataBackup(private val backupListener: BackupListener = BackupListener.Empty) {

    protected abstract suspend fun ProgressContext.runImport(inputStream: InputStream): BackupResult
    protected abstract suspend fun ProgressContext.runExport(outputStream: OutputStream): BackupResult

    private val progressContext = object : ProgressContext {
        override suspend fun initiateProgress(maxCount: Int) {
            withContext(Dispatchers.Main) {
                backupListener.initiateProgress(maxCount)
            }
        }

        override suspend fun madeProgress(progress: Int) {
            withContext(Dispatchers.Main) {
                backupListener.onProgressUpdated(progress)
            }
        }
    }

    open suspend fun execute(
        backupOperation: BackupOperation,
        backupResourceProvider: BackupResourceProvider
    ) {
        backupListener.run {
            runCatching {
                withContext(Dispatchers.Main) { onStart(backupOperation) }

                backupResourceProvider.run {
                    progressContext.run {
                        when (backupOperation) {
                            BackupOperation.IMPORT -> provideInputStream().use { runImport(it) }
                            BackupOperation.EXPORT -> provideOutputStream().use { runExport(it) }
                        }
                    }
                }

            }.onSuccess {
                withContext(Dispatchers.Main) { onSuccess(backupOperation, it) }
            }.onFailure {
                withContext(Dispatchers.Main) { onFailure(backupOperation, it) }
            }
        }
    }
}