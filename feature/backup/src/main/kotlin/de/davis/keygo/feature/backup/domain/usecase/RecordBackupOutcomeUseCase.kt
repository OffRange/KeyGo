package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.failureReason
import de.davis.keygo.feature.backup.domain.model.retryable
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import org.koin.core.annotation.Single

@Single
internal class RecordBackupOutcomeUseCase(
    private val jobRepository: BackupJobRepository,
    private val cleanupBackupResources: CleanupBackupResourcesUseCase,
) {

    /**
     * [canRetry] is false on the job's last attempt: a retryable error then becomes terminal, so the
     * record closes and the cleanup below can hand back the job's credentials.
     */
    suspend operator fun invoke(
        workId: WorkId,
        terminal: ExportProgress?,
        canRetry: Boolean = true,
    ) {
        val result = when (terminal) {
            is ExportProgress.Succeeded -> BackupResult.Success
            is ExportProgress.Failed -> when {
                terminal.error.retryable && canRetry -> return
                // failureReason is null exactly for the retryable errors, which reach here only
                // once their attempts are spent.
                else -> BackupResult.Failure(
                    terminal.error.failureReason ?: BackupFailureReason.RetriesExhausted,
                )
            }

            else -> return
        }
        jobRepository.markFinished(workId, result)

        // A recurring schedule still needs its credentials for the next run - but a run finishing
        // is a good moment to notice that some *other* job no longer needs its own.
        if (workId == BackupWorker.RECURRING_WORK_ID) cleanupBackupResources.reconcile()
        else cleanupBackupResources(workId)
    }
}
