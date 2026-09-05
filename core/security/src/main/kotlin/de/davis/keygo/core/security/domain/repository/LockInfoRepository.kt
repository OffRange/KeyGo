package de.davis.keygo.core.security.domain.repository

import de.davis.keygo.core.security.domain.model.LockInfo
import kotlinx.coroutines.flow.Flow

interface LockInfoRepository {

    suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout)
    suspend fun setBackgroundedAt(backgroundedAt: Long)

    fun observeLockInfo(): Flow<LockInfo>
}