package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.domain.repository.DispatchedBackupRepository
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
        repository.observe().map { statuses ->
            // One read of the job store per emission, not one per row.
            val jobs = jobRepository.getJobs()
            statuses.map { it.enrich(jobs) }
        }

    private suspend fun BackupWorkStatus.enrich(jobs: Map<WorkId, BackupJob>): DispatchedBackup {
        val workId: WorkId = when (kind) {
            DispatchedBackup.Kind.Recurring -> BackupWorker.RECURRING_WORK_ID
            DispatchedBackup.Kind.OneTime -> id
        }
        val job = jobs[workId]
        return DispatchedBackup(
            id = id,
            kind = kind,
            state = state,
            format = job?.format,
            destination = job?.let { destinationResolver.resolve(it.uri) },
            timestamp = job?.let { it.finishedAt ?: it.createdAt } ?: 0L,
            // Read from the persisted result, not the live state (a failed recurring run is ENQUEUED
            // again by the time it is observed, so its record is the only witness) - but a cancelled
            // schedule keeps its last failure on the record, and a red reason under a "Cancelled"
            // pill reads as a contradiction, so suppress it there.
            failureReason = (job?.lastResult as? BackupResult.Failure)
                ?.takeIf { state != DispatchedBackup.State.Cancelled }
                ?.reason,
        )
    }
}
