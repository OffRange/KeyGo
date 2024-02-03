package de.davis.passwordmanager.backup

interface PasswordProvider {

    suspend operator fun invoke(
        backupOperation: BackupOperation,
        callback: suspend (password: String) -> Unit
    )
}