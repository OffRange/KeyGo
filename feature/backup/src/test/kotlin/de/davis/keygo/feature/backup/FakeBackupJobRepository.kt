package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.reository.retainedJobKeys
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeBackupJobRepository(
    private val now: () -> Long = { 0L },
) : BackupJobRepository {
    val jobs = mutableMapOf<String, BackupJob>()

    override suspend fun getJob(workId: String): BackupJob? = jobs[workId]

    override suspend fun getJobs(): Map<String, BackupJob> = jobs.toMap()

    override fun observeJobs(): Flow<List<BackupJob>> = flow { emit(jobs.values.toList()) }

    override suspend fun putJob(workId: String, job: BackupJob): Result<Unit, Unit> {
        jobs[workId] = job.copy(createdAt = now())
        return Result.Success(Unit)
    }

    override suspend fun markFinished(workId: String, result: BackupResult, finishedAt: Long) {
        val existing = jobs[workId] ?: return
        upsertAndPrune(workId, existing.copy(finishedAt = finishedAt, lastResult = result))
    }

    override suspend fun markCancelled(workId: String, cancelledAt: Long) {
        val existing = jobs[workId] ?: return
        upsertAndPrune(workId, existing.copy(cancelled = true, finishedAt = cancelledAt))
    }

    private fun upsertAndPrune(workId: String, job: BackupJob) {
        jobs[workId] = job
        val keep = retainedJobKeys(jobs.mapValues { it.value.finishedAt })
        jobs.keys.retainAll(keep)
    }

    override suspend fun clearPassphrase(workId: String) {
        val existing = jobs[workId] ?: return
        jobs[workId] = existing.copy(wrappedPassphrase = null)
    }
}
