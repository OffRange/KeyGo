package de.davis.keygo.feature.backup.data

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import de.davis.keygo.feature.backup.worker.BackupWorker
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

@Single
internal class BackupSchedulerImpl(
    private val workManager: WorkManager,
) : BackupScheduler {

    private val constraints by lazy {
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    }

    override fun scheduleRecurringBackup(interval: BackupInterval) {
        val repeat = when (interval.unit) {
            IntervalUnit.Days -> interval.count.days
            IntervalUnit.Weeks -> interval.count.days * 7
        }.toJavaDuration()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(repeat)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = UNIQUE_WORK_NAME,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = request
        )
    }

    override fun scheduleOneTimeBackup() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(request = request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "backup_worker"
    }
}