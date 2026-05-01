package de.davis.keygo.feature.vault.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface MoveItemsError {
    data class VaultNotFound(val vaultId: VaultId) : MoveItemsError
    data class ItemMoveFailed(val itemId: ItemId, val cause: Throwable) : MoveItemsError
}
