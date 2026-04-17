package de.davis.keygo.rust.derive

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.KeyDerivationException
import de.davisalessandro.keygo.rust.KeyDeriverInterface
import de.davisalessandro.keygo.rust.RootKek

typealias KeyDeriver = KeyDeriverInterface

fun KeyDeriver.deriveRootKekFromPasswordWithResult(
    password: String,
    salt: ByteArray,
): Result<RootKek, KeyDerivationException> = runCatching {
    deriveRootKekFromPassword(password, salt)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyDerivationException) }
)