package de.davis.keygo.feature.vault.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface MoveItemsError {
    data class VaultNotFound(val vaultId: VaultId) : MoveItemsError

    /** A single item failed to re-wrap; no DB writes happened, source vault is untouched. */
    data class ItemMoveFailed(val itemId: ItemId, val cause: Throwable) : MoveItemsError

    /** The atomic bulk write failed; the transaction rolled back and no items were moved. */
    data class PersistFailed(val cause: Throwable) : MoveItemsError
}
