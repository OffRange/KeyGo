package de.davis.keygo.feature.backup.data.reository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobs
import de.davis.keygo.feature.backup.data.local.model.copy
import de.davis.keygo.feature.backup.data.mapper.toDomain
import de.davis.keygo.feature.backup.data.mapper.toProto
import de.davis.keygo.feature.backup.di.annotation.BackupJobsQualifier
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class BackupJobRepositoryImpl(
    @param:BackupJobsQualifier
    private val dataStore: DataStore<ProtoBackupJobs>,
) : BackupJobRepository {

    override suspend fun getJob(workId: String): BackupJob? =
        dataStore.data.map { it.jobsMap[workId]?.toDomain() }.firstOrNull()

    override suspend fun putJob(workId: String, job: BackupJob): Result<Unit, Unit> = runCatching {
        dataStore.updateData { current ->
            current.copy { jobs[workId] = job.toProto() }
        }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(Unit) }
    )
}