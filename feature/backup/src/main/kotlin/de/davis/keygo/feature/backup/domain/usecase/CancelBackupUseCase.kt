package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.domain.repository.DispatchedBackupRepository
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

        val workId: WorkId = when (kind) {
            DispatchedBackup.Kind.Recurring -> BackupWorker.RECURRING_WORK_ID
            DispatchedBackup.Kind.OneTime -> id
        }
        jobRepository.markCancelled(workId)
        cleanupBackupResources(workId)
    }
}
