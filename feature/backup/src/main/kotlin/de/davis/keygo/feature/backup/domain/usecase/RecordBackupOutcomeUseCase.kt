package de.davis.keygo.feature.backup.domain.usecase

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

    suspend operator fun invoke(workId: String, terminal: ExportProgress?) {
        val result = when (terminal) {
            is ExportProgress.Succeeded -> BackupResult.Success
            is ExportProgress.Failed ->
                if (terminal.error.retryable) return
                else BackupResult.Failure(terminal.error.failureReason)

            else -> return
        }
        jobRepository.markFinished(workId, result)

        // A recurring schedule still needs its credentials for the next run.
        if (workId != BackupWorker.RECURRING_WORK_ID) cleanupBackupResources(workId)
    }
}
