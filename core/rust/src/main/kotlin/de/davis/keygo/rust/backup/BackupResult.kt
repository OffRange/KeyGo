package de.davis.keygo.rust.backup

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a UniFFI backup call off the caller's thread and folds it into a [Result].
 *
 * Every backup entry point in the Rust bindings throws [BackupException] and nothing else, so the
 * cast is total for anything the FFI itself raises.
 */
internal suspend inline fun <T> backupResult(
    crossinline block: () -> T,
): Result<T, BackupException> = withContext(Dispatchers.Default) {
    runCatching { block() }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as BackupException) },
    )
}
