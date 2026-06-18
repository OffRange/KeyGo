package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.BackupJob

interface BackupScheduler {

    suspend fun scheduleRecurringBackup(
        job: BackupJob,
        interval: BackupInterval
    ): Result<Unit, Unit>

    suspend fun scheduleOneTimeBackup(job: BackupJob): Result<Unit, Unit>
    fun cancel()
}