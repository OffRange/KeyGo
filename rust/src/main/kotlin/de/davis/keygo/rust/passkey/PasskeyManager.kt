package de.davis.keygo.rust.passkey

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.PasskeyException
import de.davisalessandro.keygo.rust.RegistrationResponse
import de.davisalessandro.keygo.rust.RustPasskeyInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun RustPasskeyInterface.authenticateWithResult(
    requestJson: String,
    passkey: ByteArray,
    clientDataHash: ByteArray
): Result<String, PasskeyException> = withContext(Dispatchers.Default) {
    runCatching {
        authenticate(requestJson, passkey, clientDataHash)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as PasskeyException) }
    )
}

suspend fun RustPasskeyInterface.registerWithResult(
    requestJson: String
): Result<RegistrationResponse, PasskeyException> = withContext(Dispatchers.Default) {
    runCatching {
        register(requestJson)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as PasskeyException) }
    )
}

suspend fun RustPasskeyInterface.getExcludedCredentialIds(
    requestJson: String
): Result<List<ByteArray>, PasskeyException> = withContext(Dispatchers.Default) {
    runCatching {
        excludedCredentials(requestJson)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as PasskeyException) }
    )
}

typealias PasskeyManager = RustPasskeyInterface