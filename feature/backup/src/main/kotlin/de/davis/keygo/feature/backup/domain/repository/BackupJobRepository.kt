package de.davis.keygo.feature.backup.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupJob

interface BackupJobRepository {

    suspend fun getJob(workId: String): BackupJob?
    suspend fun putJob(workId: String, job: BackupJob): Result<Unit, Unit>
}