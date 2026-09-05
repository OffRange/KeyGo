package de.davis.keygo.core.security

import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeLockInfoRepository(
    initLockInfo: LockInfo = LockInfo(
        autoLockTimeout = LockInfo.Timeout.IMMEDIATELY,
        backgroundedAt = 0L,
    ),
) : LockInfoRepository {

    private val lockInfo = MutableStateFlow(initLockInfo)

    override suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout) {
        lockInfo.update { it.copy(autoLockTimeout = timeout) }
    }

    override suspend fun setBackgroundedAt(backgroundedAt: Long) {
        lockInfo.update { it.copy(backgroundedAt = backgroundedAt) }
    }

    override fun observeLockInfo(): Flow<LockInfo> = lockInfo.asStateFlow()
}
