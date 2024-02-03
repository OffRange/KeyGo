package de.davis.passwordmanager.backup.listener

import de.davis.passwordmanager.backup.BackupOperation
import de.davis.passwordmanager.backup.BackupResult

interface BackupListener {
    fun onStart(backupOperation: BackupOperation)
    fun onSuccess(backupOperation: BackupOperation, backupResult: BackupResult)
    fun onFailure(backupOperation: BackupOperation, throwable: Throwable)

    fun initiateProgress(maxCount: Int) {}

    fun onProgressUpdated(progress: Int) {}

    data object Empty : BackupListener {
        override fun onStart(backupOperation: BackupOperation) {}
        override fun onSuccess(backupOperation: BackupOperation, backupResult: BackupResult) {}
        override fun onFailure(backupOperation: BackupOperation, throwable: Throwable) {}
    }
}