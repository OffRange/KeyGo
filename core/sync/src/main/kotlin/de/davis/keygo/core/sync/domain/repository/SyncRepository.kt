package de.davis.keygo.core.sync.domain.repository

import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result

interface SyncRepository {

    suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError>
}