package de.davis.keygo.rust.passkey

import de.davis.keygo.core.util.Result
import de.davis.keygo.rust.passkey.model.KeyGoRegistrationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasskeyManager {

    init {
        System.loadLibrary("keygo_rust")
    }

    suspend fun authenticate(
        requestJson: String,
        passkey: ByteArray,
        clientDataHash: ByteArray
    ): Result<String, Throwable> = withContext(Dispatchers.Default) {
        runCatching {
            authenticatePasskey(requestJson, passkey, clientDataHash)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) }
        )
    }

    suspend fun register(requestJson: String): Result<KeyGoRegistrationResponse, Throwable> =
        withContext(Dispatchers.Default) {
            runCatching {
                registerPasskey(requestJson)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { Result.Failure(it) }
            )
        }

    suspend fun getExcludedCredentialIds(requestJson: String): Result<Array<ByteArray>, Throwable> =
        withContext(Dispatchers.Default) {
            runCatching {
                getExcludedCredentials(requestJson)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { Result.Failure(it) }
            )
        }

    private external fun authenticatePasskey(
        requestJson: String,
        passkey: ByteArray,
        clientDataHash: ByteArray
    ): String

    private external fun registerPasskey(requestJson: String): KeyGoRegistrationResponse
    private external fun getExcludedCredentials(requestJson: String): Array<ByteArray>
}