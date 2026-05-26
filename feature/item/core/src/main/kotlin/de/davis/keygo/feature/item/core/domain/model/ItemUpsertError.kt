package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.security.domain.model.CryptoScopeError

/**
 * Errors surfaced when creating or updating any vault item. Intentionally an open (non-sealed)
 * interface so item-type-specific errors can contribute their own variants while sharing this
 * common return channel. Call sites inspect it with `contains`/`is` checks rather than exhaustive
 * `when`.
 */
interface ItemUpsertError {
    data object BlankName : ItemUpsertError
    data object Empty : ItemUpsertError
    data object InvalidVaultId : ItemUpsertError
    data object InvalidItemId : ItemUpsertError
    data class CryptoError(val error: CryptoScopeError) : ItemUpsertError
    data class DatabaseError(val throwable: Throwable) : ItemUpsertError
}
