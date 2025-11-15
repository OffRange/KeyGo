package de.davis.keygo.rust.passkey

import de.davis.keygo.core.util.Result
import de.davis.keygo.rust.passkey.model.KeyGoRegistrationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasskeyManager {

    init {
        System.loadLibrary("keygo_rust")
    }

    suspend fun register(requestJson: String): Result<KeyGoRegistrationResponse, Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                registerPasskey(requestJson)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { Result.Failure(Unit) }
            )
        }

    suspend fun getExcludedCredentialIds(requestJson: String): Result<Array<ByteArray>, Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                getExcludedCredentials(requestJson)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { Result.Failure(Unit) }
            )
        }

    private external fun registerPasskey(requestJson: String): KeyGoRegistrationResponse
    private external fun getExcludedCredentials(requestJson: String): Array<ByteArray>
}