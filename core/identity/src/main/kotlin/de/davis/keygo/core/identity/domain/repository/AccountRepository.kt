package de.davis.keygo.core.identity.domain.repository

import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Persists account identity metadata and associated cryptographic key-wrapping data.
 * * This repository manages [Account] objects, which bundle non-secret identifiers (ID,
 * display name) with the sensitive, opaque blobs required to unlock account secrets
 *.
 *
 * The backing store is an account registry, allowing for multi-account
 * support by managing an active account ID. For now, the UI primarily
 * interacts with the single active account.
 *
 * Note: This repository persists the opaque key-wrapping metadata per account.
 * These contain the wrapped keys and their respective Initialization Vectors (IVs)
 */
interface AccountRepository {

    /**
     * Emits the currently active account (or null) and re-emits whenever the
     * backing registry changes.
     */
    fun observe(): Flow<Account?>

    /**
     * Returns the currently active account, or null if no account is registered.
     */
    suspend fun getOrNull(): Account?

    /**
     * Persists or updates an account, including its identity and associated
     * security key metadata.
     */
    suspend fun set(account: Account): Result<Unit, Unit>
}
