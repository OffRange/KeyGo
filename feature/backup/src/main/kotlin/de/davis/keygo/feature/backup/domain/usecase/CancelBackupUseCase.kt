package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.DispatchedBackupRepository
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import org.koin.core.annotation.Single

@Single
internal class CancelBackupUseCase(
    private val repository: DispatchedBackupRepository,
    private val jobRepository: BackupJobRepository,
    private val cleanupBackupResources: CleanupBackupResourcesUseCase,
) {

    suspend operator fun invoke(id: String, kind: DispatchedBackup.Kind) {
        repository.cancel(id)

        // Recurring work is a singleton stored under a stable key; one-time records are keyed by
        // the WorkManager id.
        val workId = when (kind) {
            DispatchedBackup.Kind.Recurring -> BackupWorker.RECURRING_WORK_ID
            DispatchedBackup.Kind.OneTime -> id
        }
        jobRepository.markCancelled(workId)
        cleanupBackupResources(workId)
    }
}
