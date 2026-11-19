package de.davis.keygo.core.sync.data.repository

import de.davis.keygo.core.sync.data.mapper.toKotlin
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
        withClient(serverUrl) { meta() }.mapSuccess { it.toKotlin() }

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
        Result.Failure(e.toSyncError())
    }
}
