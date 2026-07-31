package de.davis.keygo.feature.backup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import de.davis.keygo.feature.backup.data.mapper.toProgressData
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.retryable
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.domain.usecase.ExportBackupUseCase
import de.davis.keygo.feature.backup.domain.usecase.RecordBackupOutcomeUseCase
import org.koin.android.annotation.KoinWorker

internal fun resultFor(terminal: ExportProgress?, canRetry: Boolean): ListenableWorker.Result =
    when (terminal) {
        is ExportProgress.Succeeded -> ListenableWorker.Result.success()
        is ExportProgress.Failed ->
            if (terminal.error.retryable && canRetry) ListenableWorker.Result.retry()
            else ListenableWorker.Result.failure()

        else -> ListenableWorker.Result.failure()
    }

@KoinWorker
internal class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
    private val backupJobRepository: BackupJobRepository,
    private val exportBackup: ExportBackupUseCase,
    private val recordOutcome: RecordBackupOutcomeUseCase,
) : CoroutineWorker(appContext, params) {

    private val isRecurring = TAG_RECURRING in tags

    override suspend fun doWork(): Result {
        val workId: WorkId = if (isRecurring) RECURRING_WORK_ID else id.toString()
        val job = backupJobRepository.getJob(workId) ?: return Result.failure()

        val canRetry = runAttemptCount + 1 < MAX_ATTEMPTS

        var terminal: ExportProgress? = null
        exportBackup(job).collect { progress ->
            if (progress is ExportProgress.InFlight)
                setProgress(progress.toProgressData())
            terminal = progress
        }

        // The Rust cause exists only here - it is never persisted and never shown to the user.
        val error = (terminal as? ExportProgress.Failed)?.error
        if (error is ExportError.SerializationFailed)
            Log.e(TAG, "Backup serialization failed for $workId", error.cause)

        recordOutcome(workId, terminal, canRetry)

        return resultFor(terminal, canRetry)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "backup_worker"

        /**
         * Attempts a single dispatch gets before its retryable failures are recorded as terminal.
         * Bounded so an escrowed ARK is never held open by a job that keeps deferring forever.
         */
        const val MAX_ATTEMPTS = 5

        /** Recurring work is a singleton; one-time job records are keyed by their WorkManager id. */
        const val RECURRING_WORK_ID: WorkId = "recurring_backup"

        const val TAG = "backup"
        const val TAG_RECURRING = "backup_recurring"
        const val TAG_ONE_TIME = "backup_one_time"
    }
}
