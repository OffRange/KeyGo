package de.davis.keygo.core.security.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.security.data.local.model.ProtoLockInfo
import de.davis.keygo.core.security.data.mapper.toDomain
import de.davis.keygo.core.security.data.mapper.toProto
import de.davis.keygo.core.security.di.annotation.LockInfoQualifier
import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class LockInfoRepositoryImpl(
    @param:LockInfoQualifier
    private val dataStore: DataStore<ProtoLockInfo>
) : LockInfoRepository {

    override suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout) {
        dataStore.updateData {
            it.toBuilder()
                .setAutoLockTimeout(timeout.toProto())
                .build()
        }
    }

    override suspend fun setBackgroundedAt(backgroundedAt: Long) {
        dataStore.updateData {
            it.toBuilder()
                .setBackgroundedAt(backgroundedAt)
                .build()
        }
    }

    override suspend fun getLockInfo(): LockInfo = dataStore.data.first().toDomain()
}