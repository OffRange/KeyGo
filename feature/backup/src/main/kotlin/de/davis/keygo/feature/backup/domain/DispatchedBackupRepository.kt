package de.davis.keygo.feature.backup.domain

import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import kotlinx.coroutines.flow.Flow

interface DispatchedBackupRepository {
    fun observe(): Flow<List<BackupWorkStatus>>
    suspend fun cancel(id: String)
}
