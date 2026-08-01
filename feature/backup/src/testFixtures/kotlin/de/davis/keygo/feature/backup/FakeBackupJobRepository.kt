package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.repository.retainedJobKeys
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeBackupJobRepository(
    private val now: () -> Long = { 0L },
) : BackupJobRepository {
    val jobs = mutableMapOf<WorkId, BackupJob>()

    override suspend fun getJob(workId: WorkId): BackupJob? = jobs[workId]

    override suspend fun getJobs(): Map<WorkId, BackupJob> = jobs.toMap()

    override fun observeJobs(): Flow<List<BackupJob>> = flow { emit(jobs.values.toList()) }

    override suspend fun putJob(workId: WorkId, job: BackupJob): Result<Unit, Unit> {
        jobs[workId] = job.copy(createdAt = now())
        return Result.Success(Unit)
    }

    override suspend fun markFinished(workId: WorkId, result: BackupResult, finishedAt: Long) {
        val existing = jobs[workId] ?: return
        upsertAndPrune(workId, existing.copy(finishedAt = finishedAt, lastResult = result))
    }

    override suspend fun markCancelled(workId: WorkId, cancelledAt: Long) {
        val existing = jobs[workId] ?: return
        upsertAndPrune(workId, existing.copy(cancelled = true, finishedAt = cancelledAt))
    }

    private fun upsertAndPrune(workId: WorkId, job: BackupJob) {
        jobs[workId] = job
        val keep = retainedJobKeys(jobs.mapValues { it.value.finishedAt })
        jobs.keys.retainAll(keep)
    }

    override suspend fun clearPassphrase(workId: WorkId) {
        val existing = jobs[workId] ?: return
        jobs[workId] = existing.copy(wrappedPassphrase = null)
    }
}
