package de.davis.keygo.core.security.domain.repository

import de.davis.keygo.core.security.domain.model.LockInfo

interface LockInfoRepository {

    suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout)
    suspend fun setBackgroundedAt(backgroundedAt: Long)

    suspend fun getLockInfo(): LockInfo
}