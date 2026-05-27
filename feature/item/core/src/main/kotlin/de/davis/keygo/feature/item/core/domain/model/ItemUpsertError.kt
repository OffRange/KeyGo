package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.security.domain.model.CryptoScopeError

/**
 * Errors surfaced when creating or updating any vault item. Call sites inspect it with
 * `contains`/`is` checks rather than exhaustive `when`.
 */
interface ItemUpsertError {
    data object BlankName : ItemUpsertError
    data object Empty : ItemUpsertError
    data object InvalidVaultId : ItemUpsertError
    data object InvalidItemId : ItemUpsertError
    data class CryptoError(val error: CryptoScopeError) : ItemUpsertError
    data class DatabaseError(val throwable: Throwable) : ItemUpsertError
}
