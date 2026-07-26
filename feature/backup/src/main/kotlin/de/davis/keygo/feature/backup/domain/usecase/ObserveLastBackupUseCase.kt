package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.LastBackup
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * An interface rather than a plain use case because other feature modules observe it to show backup
 * health: the implementation's collaborators are module-internal, so an interface is what their
 * tests can substitute.
 */
fun interface ObserveLastBackupUseCase {
    operator fun invoke(): Flow<LastBackup?>
}

@Single
internal class ObserveLastBackupUseCaseImpl(
    private val jobRepository: BackupJobRepository,
) : ObserveLastBackupUseCase {

    override operator fun invoke(): Flow<LastBackup?> =
        jobRepository.observeJobs().map { jobs ->
            jobs.filter { it.lastResult == BackupResult.Success && it.finishedAt != null }
                .maxByOrNull { it.finishedAt!! }
                ?.let { LastBackup(it.finishedAt!!) }
        }
}
