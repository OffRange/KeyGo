package de.davis.keygo.feature.backup.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupSchedule

interface BackupScheduleRepository {

    suspend fun getSchedule(): BackupSchedule?
    suspend fun setSchedule(schedule: BackupSchedule): Result<Unit, Unit>
}