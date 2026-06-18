package de.davis.keygo.feature.backup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import org.koin.android.annotation.KoinWorker

@KoinWorker
internal class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
    private val backupJobRepository: BackupJobRepository,
) : CoroutineWorker(appContext, params) {

    private val isRecurring = TAG_RECURRING in tags

    override suspend fun doWork(): Result {
        // Recurring is a singleton stored under a stable key; one-time records are keyed by workId.
        val workId = if (isRecurring) RECURRING_WORK_ID else id.toString()
        val job = backupJobRepository.getJob(workId) ?: return Result.failure()

        // TODO: perform the actual export using job.uri / job.format / job.passphrase
        Log.d("BackupWorker", "doing work: $job")
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "backup_worker"
        const val RECURRING_WORK_ID = "recurring_backup"

        const val TAG = "backup"
        const val TAG_RECURRING = "backup_recurring"
        const val TAG_ONE_TIME = "backup_one_time"
    }
}