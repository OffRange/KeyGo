package de.davis.keygo.core.sync.data.repository

import android.util.Log
import de.davis.keygo.core.sync.data.mapper.toDomain
import de.davis.keygo.core.sync.data.mapper.toSyncError
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapSuccess
import de.davisalessandro.keygo.rust.DpopKey
import de.davisalessandro.keygo.rust.HttpTransport
import de.davisalessandro.keygo.rust.SyncClient
import de.davisalessandro.keygo.rust.SyncException
import de.davisalessandro.keygo.rust.TokenStore
import org.koin.core.annotation.Single

@Single
internal class SyncRepositoryImpl(
    private val transport: HttpTransport,
    private val tokens: TokenStore,
    private val dpopKey: DpopKey,
) : SyncRepository {
    
    override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> =
        withClient(serverUrl) { meta() }.mapSuccess { it.toDomain() }

    private suspend fun <R> withClient(
        serverUrl: String,
        call: suspend SyncClient.() -> R,
    ): Result<R, SyncError> = try {
        SyncClient(
            baseUrl = serverUrl,
            transport = transport,
            dpopKey = dpopKey,
            tokens = tokens,
        ).use { Result.Success(it.call()) }
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

    private companion object {
        const val TAG = "SyncRepository"
    }
}
