package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.DispatchedBackupRepository
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class ObserveDispatchedBackupsUseCase(
    private val repository: DispatchedBackupRepository,
    private val jobRepository: BackupJobRepository,
    private val destinationResolver: BackupDestinationResolver,
) {

    operator fun invoke(): Flow<List<DispatchedBackup>> =
        repository.observe().map { statuses -> statuses.map { it.enrich() } }

    private suspend fun BackupWorkStatus.enrich(): DispatchedBackup {
        val workId = when (kind) {
            DispatchedBackup.Kind.Recurring -> BackupWorker.RECURRING_WORK_ID
            DispatchedBackup.Kind.OneTime -> id
        }
        val job = jobRepository.getJob(workId)
        return DispatchedBackup(
            id = id,
            kind = kind,
            state = state,
            format = job?.format,
            destination = job?.let { destinationResolver.resolve(it.uri) },
            progress = progress,
            timestamp = job?.let { it.finishedAt ?: it.createdAt } ?: 0L,
        )
    }
}
