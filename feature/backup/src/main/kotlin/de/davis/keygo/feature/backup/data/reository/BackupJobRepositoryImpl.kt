package de.davis.keygo.feature.backup.data.reository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobs
import de.davis.keygo.feature.backup.data.local.model.copy
import de.davis.keygo.feature.backup.data.mapper.toDomain
import de.davis.keygo.feature.backup.data.mapper.toProto
import de.davis.keygo.feature.backup.di.annotation.BackupJobsQualifier
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    override suspend fun getJobs(): Map<String, BackupJob> =
        dataStore.data.first().jobsMap.mapValues { (_, proto) -> proto.toDomain() }

    override fun observeJobs(): Flow<List<BackupJob>> =
        dataStore.data.map { it.jobsMap.values.map { proto -> proto.toDomain() } }

    override suspend fun putJob(workId: String, job: BackupJob): Result<Unit, Unit> = runCatching {
        dataStore.updateData { current ->
            current.copy {
                jobs[workId] = job.copy(createdAt = System.currentTimeMillis()).toProto()
            }
        }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(Unit) },
    )

    override suspend fun markFinished(workId: String, finishedAt: Long, result: BackupResult) {
        dataStore.updateData { current ->
            val existing = current.jobsMap[workId] ?: return@updateData current
            current.copy {
                jobs[workId] = existing.copy {
                    this.finishedAt = finishedAt
                    lastResult = result.name
                }
            }
        }
    }

    override suspend fun markCancelled(workId: String, cancelledAt: Long) {
        dataStore.updateData { current ->
            val existing = current.jobsMap[workId] ?: return@updateData current
            current.copy {
                jobs[workId] = existing.copy {
                    cancelled = true
                    finishedAt = cancelledAt
                }
            }
        }
    }

    override suspend fun clearPassphrase(workId: String) {
        dataStore.updateData { current ->
            val existing = current.jobsMap[workId] ?: return@updateData current
            current.copy {
                jobs[workId] = existing.copy {
                    clearPassphraseCt()
                    clearPassphraseIv()
                }
            }
        }
    }
}
