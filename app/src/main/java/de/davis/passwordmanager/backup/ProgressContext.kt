package de.davis.passwordmanager.backup

interface ProgressContext {
    suspend fun initiateProgress(maxCount: Int)
    suspend fun madeProgress(progress: Int)
}