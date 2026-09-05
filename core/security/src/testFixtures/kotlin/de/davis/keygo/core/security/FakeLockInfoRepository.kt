package de.davis.keygo.core.security

import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeLockInfoRepository(
    initLockInfo: LockInfo = LockInfo(autoLockTimeout = LockInfo.Timeout.IMMEDIATELY),
) : LockInfoRepository {

    private val _lockInfo = MutableStateFlow(initLockInfo)

    /** The stored record, settable so a test can arrange one without going through the setters. */
    var lockInfo: LockInfo
        get() = _lockInfo.value
        set(value) {
            _lockInfo.update { value }
        }

    override suspend fun setAutoLockTimeout(timeout: LockInfo.Timeout) {
        _lockInfo.update { it.copy(autoLockTimeout = timeout) }
    }

    override fun observeLockInfo(): Flow<LockInfo> = _lockInfo.asStateFlow()
}
