package de.davis.keygo.feature.backup.data.reository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupSchedule
import de.davis.keygo.feature.backup.data.mapper.toDomain
import de.davis.keygo.feature.backup.data.mapper.toProto
import de.davis.keygo.feature.backup.di.annotation.BackupScheduleQualifier
import de.davis.keygo.feature.backup.domain.model.BackupSchedule
import de.davis.keygo.feature.backup.domain.repository.BackupScheduleRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class BackupScheduleRepositoryImpl(
    @param:BackupScheduleQualifier
    private val dataStore: DataStore<ProtoBackupSchedule>,
) : BackupScheduleRepository {

    override suspend fun getSchedule(): BackupSchedule? =
        dataStore.data.map { it.toDomain() }.firstOrNull()

    override suspend fun setSchedule(schedule: BackupSchedule): Result<Unit, Unit> = runCatching {
        dataStore.updateData { schedule.toProto() }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(Unit) }
    )
}