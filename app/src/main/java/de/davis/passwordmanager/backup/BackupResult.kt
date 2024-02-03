package de.davis.passwordmanager.backup

sealed interface BackupResult {

    open class Success : BackupResult
    data class SuccessWithDuplicates(val duplicates: Int) : Success()
}