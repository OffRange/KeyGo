package de.davis.keygo.core.sync

import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

class FakeSyncRepository : SyncRepository {

    var devices: Result<List<Device>, SyncError> = Result.Success(emptyList())

    var gate: CompletableDeferred<Unit>? = null
    var loads = 0
        private set

    var deleteDeviceResults: List<Result<Unit, SyncError>> = listOf(Result.Success(Unit))
    val deleteDeviceCalls = mutableListOf<UUID>()

    var deleteDeviceGate: CompletableDeferred<Unit>? = null

    val deletedDevices = mutableListOf<UUID>()

    var stepUpResults: List<Result<Unit, SyncError>> = listOf(Result.Success(Unit))
    val stepUpPasswords = mutableListOf<String>()

    var stepUpGate: CompletableDeferred<Unit>? = null

    override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> =
        error("checkServer is not faked")

    override suspend fun register(
        serverUrl: String,
        userId: UUID,
        username: String,
        password: String,
        invite: String?,
    ): Result<SyncAccount, SyncError> = error("register is not faked")

    override suspend fun logout(): Result<Unit, SyncError> = Result.Success(Unit)

    override suspend fun devices(): Result<List<Device>, SyncError> {
        loads++
        val answer = devices
        gate?.await()
        return answer
    }

    override suspend fun deleteDevice(deviceId: UUID): Result<Unit, SyncError> {
        val result = deleteDeviceResults.answerFor(deleteDeviceCalls.size)
        deleteDeviceCalls += deviceId
        deleteDeviceGate?.await()
        if (result is Result.Success) deletedDevices += deviceId
        return result
    }

    override suspend fun stepUp(password: String): Result<Unit, SyncError> {
        val result = stepUpResults.answerFor(stepUpPasswords.size)
        stepUpPasswords += password
        stepUpGate?.await()
        return result
    }

    private fun <T> List<T>.answerFor(call: Int): T = getOrElse(call) { last() }
}
