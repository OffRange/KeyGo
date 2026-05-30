package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.security.domain.model.CryptoScopeError

/**
 * Errors surfaced when creating or updating any vault item. When inspecting a
 * `Set<ItemUpsertError>` use `contains`/`is`; when switching on a single instance,
 * a sealed `when` gives exhaustive compile-time safety.
 */
sealed interface ItemUpsertError {
    data object BlankName : ItemUpsertError
    data object Empty : ItemUpsertError
    data object InvalidVaultId : ItemUpsertError
    data object InvalidItemId : ItemUpsertError
    data class CryptoError(val error: CryptoScopeError) : ItemUpsertError
    data class DatabaseError(val throwable: Throwable) : ItemUpsertError
}
