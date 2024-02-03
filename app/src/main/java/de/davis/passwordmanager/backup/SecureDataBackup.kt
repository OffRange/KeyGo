package de.davis.passwordmanager.backup

import de.davis.passwordmanager.backup.listener.BackupListener
import java.io.InputStream
import java.io.OutputStream

abstract class SecureDataBackup(
    private val passwordProvider: PasswordProvider,
    backupListener: BackupListener = BackupListener.Empty
) : DataBackup(backupListener) {

    private lateinit var password: String

    protected abstract suspend fun ProgressContext.runImport(
        inputStream: InputStream,
        password: String
    ): BackupResult

    protected abstract suspend fun ProgressContext.runExport(
        outputStream: OutputStream,
        password: String
    ): BackupResult

    final override suspend fun ProgressContext.runImport(inputStream: InputStream): BackupResult =
        runImport(inputStream, password)

    final override suspend fun ProgressContext.runExport(outputStream: OutputStream): BackupResult =
        runExport(outputStream, password)

    override suspend fun execute(backupOperation: BackupOperation, streamProvider: StreamProvider) {
        passwordProvider(backupOperation) {
            password = it
            super.execute(backupOperation, streamProvider)
        }
    }
}