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

    private external fun registerPasskey(requestJson: String): KeyGoRegistrationResponse
}