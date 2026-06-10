package de.davis.keygo.rust.derive

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.KeyDerivationException
import de.davisalessandro.keygo.rust.KeyDeriverInterface
import de.davisalessandro.keygo.rust.RootKek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias KeyDeriver = KeyDeriverInterface

suspend fun KeyDeriver.deriveRootKekFromPasswordWithResult(
    password: String,
    salt: ByteArray,
): Result<RootKek, KeyDerivationException> = withContext(Dispatchers.Default) {
    runCatching {
        deriveRootKekFromPassword(password, salt)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as KeyDerivationException) }
    )
}
