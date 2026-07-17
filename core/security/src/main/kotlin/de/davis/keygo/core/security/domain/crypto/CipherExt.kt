package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

suspend fun Cipher.suspendDoFinal(input: ByteArray): Result<ByteArray, Throwable> =
    withContext(Dispatchers.Default) {
        runCatching {
            doFinal(input)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) }
        )
    }