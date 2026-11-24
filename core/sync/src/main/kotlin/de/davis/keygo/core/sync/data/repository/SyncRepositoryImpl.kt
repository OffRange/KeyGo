package de.davis.keygo.core.sync.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import de.davis.keygo.core.security.domain.RawArkSession
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.sync.data.mapper.toDomain
import de.davis.keygo.core.sync.data.mapper.toSyncError
import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapSuccess
import de.davisalessandro.keygo.rust.DeviceDetails
import de.davisalessandro.keygo.rust.DpopKey
import de.davisalessandro.keygo.rust.HttpTransport
import de.davisalessandro.keygo.rust.StoredSession
import de.davisalessandro.keygo.rust.SyncClient
import de.davisalessandro.keygo.rust.SyncException
import de.davisalessandro.keygo.rust.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.util.UUID

@Single
internal class SyncRepositoryImpl(
    private val context: Context,
    private val session: Session,
    private val transport: HttpTransport,
    private val tokens: TokenStore,
    private val dpopKey: DpopKey,
) : SyncRepository {

    private val clientLock = Mutex()
    private var cached: CachedClient? = null

    override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> =
        catching { newClient(serverUrl).use { it.meta() } }.mapSuccess { it.toDomain() }

    @OptIn(RawArkSession::class)
    override suspend fun register(
        serverUrl: String,
        userId: UUID,
        username: String,
        password: String,
        invite: String?,
    ): Result<SyncAccount, SyncError> = catching {
        clientFor(serverUrl).register(
            arkSession = session.arkSession(),
            userId = userId,
            username = username,
            password = password,
            invite = invite,
            device = deviceDetails(),
        )
    }.mapSuccess { it.toDomain() }

    override suspend fun logout(): Result<Unit, SyncError> =
        when (val result = withSessionClient { client, _ -> client.logout() }) {
            is Result.Failure if result.error == SyncError.SignedOut(reason = null) ->
                Result.Success(Unit)

            else -> result
        }

    override suspend fun devices(): Result<List<Device>, SyncError> =
        withSessionClient { client, stored ->
            client.devices().map { it.toDomain(currentDeviceId = stored.deviceId) }
        }

    override suspend fun deleteDevice(deviceId: UUID): Result<Unit, SyncError> =
        withSessionClient { client, _ -> client.deleteDevice(deviceId) }

    override suspend fun stepUp(password: String): Result<Unit, SyncError> =
        withSessionClient { client, _ -> client.stepUp(password) }

    private suspend fun clientFor(serverUrl: String): SyncClient = clientLock.withLock {
        cached?.takeIf { it.serverUrl == serverUrl }?.client
            ?: newClient(serverUrl).also { client ->
                cached?.client?.close()
                cached = CachedClient(serverUrl, client)
            }
    }

    private suspend fun <R> withSessionClient(
        call: suspend (SyncClient, StoredSession) -> R,
    ): Result<R, SyncError> {
        val stored = tokens.load() ?: return Result.Failure(SyncError.SignedOut(reason = null))
        return catching { call(clientFor(stored.serverUrl), stored) }
    }

    private fun newClient(serverUrl: String) = SyncClient(
        baseUrl = serverUrl,
        transport = transport,
        dpopKey = dpopKey,
        tokens = tokens,
    )

    private fun deviceDetails() = DeviceDetails(
        name = Build.MODEL,
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            .orEmpty(),
    )

    private suspend fun <R> catching(call: suspend () -> R): Result<R, SyncError> = try {
        Result.Success(call())
    } catch (e: SyncException) {
        Result.Failure(e.toSyncError().also(::logIfUnexpected))
    }

    /**
     * Network failures and KeyGo's own error codes are expected and already shown to the user;
     * only a bug or a response from something other than KeyGo is worth a log line.
     */
    private fun logIfUnexpected(error: SyncError) {
        when (error) {
            is SyncError.Internal,
            is SyncError.InvalidResponse,
            is SyncError.UnexpectedStatus,
                -> Log.w(TAG, "Sync call failed: $error")

            else -> Unit
        }
    }

    private class CachedClient(val serverUrl: String, val client: SyncClient)

    private companion object {
        const val TAG = "SyncRepository"
    }
}
