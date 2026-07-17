package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.LastBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class ObserveLastBackupUseCase(
    private val jobRepository: BackupJobRepository,
    private val destinationResolver: BackupDestinationResolver,
) {

    operator fun invoke(): Flow<LastBackup?> =
        jobRepository.observeJobs().map { jobs ->
            jobs.filter { it.lastResult == BackupResult.Success && it.finishedAt != null }
                .maxByOrNull { it.finishedAt!! }
                ?.let { LastBackup(it.finishedAt!!, destinationResolver.resolve(it.uri)) }
        }
}
