package de.davis.keygo.core.sync.domain.repository

import de.davis.keygo.core.sync.domain.model.Device
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

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<SyncAccount, SyncError>

    /** Ends the session, also locally if the server is unreachable. Already signed out is a success. */
    suspend fun logout(): Result<Unit, SyncError>

    suspend fun devices(): Result<List<Device>, SyncError>

    suspend fun deleteDevice(deviceId: UUID): Result<Unit, SyncError>

    suspend fun stepUp(password: String): Result<Unit, SyncError>

    suspend fun deleteAccount(password: String): Result<Unit, SyncError>
}
