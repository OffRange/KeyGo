package de.davis.keygo.feature.backup.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import kotlinx.coroutines.flow.Flow

interface BackupJobRepository {

    suspend fun getJob(workId: WorkId): BackupJob?
    suspend fun getJobs(): Map<WorkId, BackupJob>
    suspend fun putJob(workId: WorkId, job: BackupJob): Result<Unit, Unit>
    suspend fun markFinished(
        workId: WorkId,
        result: BackupResult,
        finishedAt: Long = System.currentTimeMillis()
    )

    suspend fun markCancelled(workId: WorkId, cancelledAt: Long = System.currentTimeMillis())
    suspend fun clearPassphrase(workId: WorkId)
    fun observeJobs(): Flow<List<BackupJob>>
}
