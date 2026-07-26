package de.davis.keygo.feature.backup.data

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

@Single
internal class BackupSchedulerImpl(
    private val workManager: WorkManager,
    private val backupJobRepository: BackupJobRepository,
) : BackupScheduler {

    private val constraints by lazy {
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    }

    override suspend fun scheduleRecurringBackup(
        job: BackupJob,
        interval: BackupInterval,
    ): Result<Unit, Unit> {
        val repeat = when (interval.unit) {
            IntervalUnit.Days -> interval.count.days
            IntervalUnit.Weeks -> interval.count.days * 7
        }.toJavaDuration()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(repeat)
            .setConstraints(constraints)
            .addTag(BackupWorker.TAG)
            .addTag(BackupWorker.TAG_RECURRING)
            .build()

        // Recurring is a singleton: store under a stable key so a later UPDATE (which preserves the
        // existing work id, not request.id) never orphans the record. The worker resolves it via
        // TAG_RECURRING. Persist before enqueue so the worker can never start ahead of its record.
        return backupJobRepository.putJob(BackupWorker.RECURRING_WORK_ID, job)
            .onSuccess {
                workManager.enqueueUniquePeriodicWork(
                    uniqueWorkName = BackupWorker.UNIQUE_WORK_NAME,
                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                    request = request,
                )
            }
    }

    override suspend fun scheduleOneTimeBackup(job: BackupJob): Result<Unit, Unit> {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .addTag(BackupWorker.TAG)
            .addTag(BackupWorker.TAG_ONE_TIME)
            .build()

        return backupJobRepository.putJob(request.id.toString(), job).onSuccess {
            workManager.enqueue(request)
        }
    }

    override fun cancel() {
        workManager.cancelUniqueWork(BackupWorker.UNIQUE_WORK_NAME)
    }
}