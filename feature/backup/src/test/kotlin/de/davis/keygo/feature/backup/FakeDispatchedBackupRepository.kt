package de.davis.keygo.feature.backup

import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.repository.DispatchedBackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDispatchedBackupRepository : DispatchedBackupRepository {
    val statuses = MutableStateFlow<List<BackupWorkStatus>>(emptyList())
    val cancelledIds = mutableListOf<String>()

    override fun observe(): Flow<List<BackupWorkStatus>> = statuses.asStateFlow()
    override suspend fun cancel(id: String) {
        cancelledIds += id
    }
}
