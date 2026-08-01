package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.alias.WorkId
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
    private val oneTimeWorkId: WorkId = "one-time",
) : BackupScheduler {

    var recurringJob: BackupJob? = null
    var recurringInterval: BackupInterval? = null
    var oneTimeJob: BackupJob? = null
    var cancelled = false
    var result: Result<Unit, Unit> = Result.Success(Unit)

    private val scheduled = mutableSetOf<WorkId>()
    private val abandoned = mutableSetOf<WorkId>()

    /** When set, [outstandingWorkIds] throws - the "scheduler unreadable" case. */
    var outstandingFailure: Throwable? = null

    /**
     * Defaults to every id [jobRepository] knows about (or, without one, every id scheduled through
     * this fake): a healthy scheduler whose view agrees with the records. [abandon] takes an id back
     * out, which is how a job the platform quietly gave up on looks - the record still reads live,
     * but no run can ever come of it.
     */
    override suspend fun outstandingWorkIds(): Set<WorkId> {
        outstandingFailure?.let { throw it }
        return (jobRepository?.jobs?.keys ?: scheduled) - abandoned
    }

    fun abandon(workId: WorkId) {
        abandoned += workId
    }

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
    private suspend fun persist(workId: WorkId, job: BackupJob): Result<Unit, Unit> {
        gate?.await()
        if (result is Result.Failure) return result
        jobRepository?.putJob(workId, job)
        scheduled += workId
        return result
    }
}
