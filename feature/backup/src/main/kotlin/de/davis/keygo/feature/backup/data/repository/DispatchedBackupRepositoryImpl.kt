package de.davis.keygo.feature.backup.data.repository

import androidx.work.WorkManager
import de.davis.keygo.feature.backup.data.mapper.toStatus
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.repository.DispatchedBackupRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import java.util.UUID

@Single
internal class DispatchedBackupRepositoryImpl(
    private val workManager: WorkManager,
) : DispatchedBackupRepository {

    override fun observe(): Flow<List<BackupWorkStatus>> =
        workManager.getWorkInfosByTagFlow(BackupWorker.TAG)
            .map { infos -> infos.map { it.toStatus() } }

    override suspend fun cancel(id: String) {
        workManager.cancelWorkById(UUID.fromString(id))
    }
}