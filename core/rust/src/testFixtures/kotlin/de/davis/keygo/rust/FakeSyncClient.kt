package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Account
import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.DeviceDetails
import de.davisalessandro.keygo.rust.DeviceId
import de.davisalessandro.keygo.rust.ServerInfo
import de.davisalessandro.keygo.rust.SyncClientInterface
import de.davisalessandro.keygo.rust.SyncDevice
import de.davisalessandro.keygo.rust.UserId

/**
 * Each method returns its result var, or throws it when it is a failure. Results without a useful
 * default stay null until a test sets them, and reading one then fails loudly.
 */
class FakeSyncClient : SyncClientInterface {

    var deleteDeviceResult: Result<Unit> = Result.success(Unit)
    var devicesResult: Result<List<SyncDevice>> = Result.success(emptyList())
    var loginResult: Result<Account>? = null
    var logoutResult: Result<Unit> = Result.success(Unit)
    var metaResult: Result<ServerInfo>? = null
    var registerResult: Result<Account>? = null
    var stepUpResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteDevice(id: DeviceId) = deleteDeviceResult.getOrThrow()

    override suspend fun devices(): List<SyncDevice> = devicesResult.getOrThrow()

    override suspend fun login(
        ark: ArkSession,
        username: String,
        password: String,
        device: DeviceDetails,
    ): Account = checkNotNull(loginResult) { "loginResult not set" }.getOrThrow()

    override suspend fun logout() = logoutResult.getOrThrow()

    override suspend fun meta(): ServerInfo =
        checkNotNull(metaResult) { "metaResult not set" }.getOrThrow()

    override suspend fun register(
        ark: ArkSession,
        userId: UserId,
        username: String,
        password: String,
        invite: String?,
        device: DeviceDetails,
    ): Account = checkNotNull(registerResult) { "registerResult not set" }.getOrThrow()

    override suspend fun stepUp(password: String) = stepUpResult.getOrThrow()
}
