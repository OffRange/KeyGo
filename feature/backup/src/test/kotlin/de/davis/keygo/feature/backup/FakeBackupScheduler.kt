package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.CompletableDeferred

/**
 * By default this just records the last scheduled job. Pass [jobRepository] to also persist the job
 * record - mirroring [de.davis.keygo.feature.backup.data.BackupSchedulerImpl] which calls
 * `backupJobRepository.putJob(...)` before enqueueing - so a cleanup reading the same repository
 * can observe the job as "live". Pass [gate] to park inside scheduling *before* that record is
 * written, reproducing the TOCTOU window between escrow provisioning and the record write.
 */
class FakeBackupScheduler(
    private val jobRepository: FakeBackupJobRepository? = null,
    private val gate: CompletableDeferred<Unit>? = null,
    private val oneTimeWorkId: String = "one-time",
) : BackupScheduler {

    var recurringJob: BackupJob? = null
    var recurringInterval: BackupInterval? = null
    var oneTimeJob: BackupJob? = null
    var cancelled = false
    var result: Result<Unit, Unit> = Result.Success(Unit)

    override suspend fun scheduleRecurringBackup(
        job: BackupJob,
        interval: BackupInterval,
    ): Result<Unit, Unit> {
        recurringJob = job
        recurringInterval = interval
        return persist(BackupWorker.RECURRING_WORK_ID, job)
    }

    override suspend fun scheduleOneTimeBackup(job: BackupJob): Result<Unit, Unit> {
        oneTimeJob = job
        return persist(oneTimeWorkId, job)
    }

    override fun cancel() {
        cancelled = true
    }

    // Mirror BackupSchedulerImpl: on success the record is written (putJob), on failure it is not,
    // so a failed schedule leaves no record - exactly the case the URI-grant release compensates.
    private suspend fun persist(workId: String, job: BackupJob): Result<Unit, Unit> {
        gate?.await()
        if (result is Result.Failure) return result
        jobRepository?.putJob(workId, job)
        return result
    }
}
