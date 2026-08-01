package de.davis.keygo.feature.backup.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJob
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobKt
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobs
import de.davis.keygo.feature.backup.data.local.model.copy
import de.davis.keygo.feature.backup.data.mapper.toDomain
import de.davis.keygo.feature.backup.data.mapper.toProto
import de.davis.keygo.feature.backup.data.mapper.writeResult
import de.davis.keygo.feature.backup.di.annotation.BackupJobsQualifier
import de.davis.keygo.feature.backup.domain.alias.WorkId
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

    override suspend fun getJob(workId: WorkId): BackupJob? =
        dataStore.data.map { it.jobsMap[workId]?.toDomain() }.firstOrNull()

    override suspend fun getJobs(): Map<WorkId, BackupJob> =
        dataStore.data.first().jobsMap.mapValues { (_, proto) -> proto.toDomain() }

    override fun observeJobs(): Flow<List<BackupJob>> =
        dataStore.data.map { it.jobsMap.values.map { proto -> proto.toDomain() } }

    override suspend fun putJob(workId: WorkId, job: BackupJob): Result<Unit, Unit> = runCatching {
        dataStore.updateData { current ->
            current.copy {
                jobs[workId] = job.copy(createdAt = System.currentTimeMillis()).toProto()
            }
        }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(Unit) },
    )

    override suspend fun markFinished(workId: WorkId, result: BackupResult, finishedAt: Long) =
        updateJob(workId) {
            this.finishedAt = finishedAt
            writeResult(result)
        }

    override suspend fun markCancelled(workId: WorkId, cancelledAt: Long) = updateJob(workId) {
        cancelled = true
        finishedAt = cancelledAt
    }

    // Clearing credentials must not prune: the record is still live and its retention position has
    // not changed.
    override suspend fun clearPassphrase(workId: WorkId) = updateJob(workId, prune = false) {
        clearPassphraseCt()
        clearPassphraseIv()
    }

    /** Applies [edit] to the record under [workId]. A missing record is a no-op. */
    private suspend fun updateJob(
        workId: WorkId,
        prune: Boolean = true,
        edit: ProtoBackupJobKt.Dsl.() -> Unit,
    ) {
        dataStore.updateData { current ->
            val existing = current.jobsMap[workId] ?: return@updateData current
            val updated = existing.copy(edit)
            if (prune) current.upsertAndPrune(workId, updated)
            else current.copy { jobs[workId] = updated }
        }
    }

    // Writes [job] under [workId], then drops finished one-time records beyond the retention cap so the
    // store cannot grow without bound as WorkManager silently prunes its own history.
    private fun ProtoBackupJobs.upsertAndPrune(
        workId: WorkId,
        job: ProtoBackupJob
    ): ProtoBackupJobs {
        val merged = jobsMap + (workId to job)
        val keep = retainedJobKeys(
            merged.mapValues { (_, j) -> if (j.hasFinishedAt()) j.finishedAt else null },
        )
        return copy {
            jobs.clear()
            jobs.putAll(merged.filterKeys { it in keep })
        }
    }
}
