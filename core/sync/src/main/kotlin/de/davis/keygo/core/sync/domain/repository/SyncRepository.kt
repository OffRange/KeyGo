package de.davis.keygo.core.sync.domain.repository

import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result
import java.util.UUID

interface SyncRepository {

    suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError>

    suspend fun register(
        serverUrl: String,
        userId: UUID,
        username: String,
        password: String,
        invite: String?,
    ): Result<SyncAccount, SyncError>

    suspend fun logout(): Result<Unit, SyncError>
}
