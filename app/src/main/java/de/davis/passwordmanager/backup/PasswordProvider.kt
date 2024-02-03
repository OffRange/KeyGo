package de.davis.passwordmanager.backup

interface PasswordProvider {

    suspend operator fun invoke(
        backupOperation: BackupOperation,
        backupResourceProvider: BackupResourceProvider,
        callback: suspend (password: String) -> Unit
    )
}