package de.davis.keygo.feature.backup.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import kotlinx.coroutines.flow.Flow

interface BackupJobRepository {

    suspend fun getJob(workId: String): BackupJob?
    suspend fun getJobs(): Map<String, BackupJob>
    suspend fun putJob(workId: String, job: BackupJob): Result<Unit, Unit>
    suspend fun markFinished(
        workId: String,
        result: BackupResult,
        finishedAt: Long = System.currentTimeMillis()
    )

    suspend fun markCancelled(workId: String, cancelledAt: Long = System.currentTimeMillis())
    suspend fun clearPassphrase(workId: String)
    fun observeJobs(): Flow<List<BackupJob>>
}
