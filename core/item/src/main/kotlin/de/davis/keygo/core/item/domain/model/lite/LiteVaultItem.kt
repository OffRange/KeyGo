package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId

/**
 * A lightweight representation of a item stored in the vault.
 */
interface LiteItem {
    val vaultItemId: ItemId
    val name: String

    @ConsistentCopyVisibility
    data class Concrete internal constructor(
        override val vaultItemId: ItemId,
        override val name: String,
    ) : LiteItem
}

/**
 * A lightweight representation of a vault item. This interface was introduced to allow exhausted
 * when expressions for LiteItem without including search results that might be represented by a
 * [LiteItem].
 *
 * @see LiteItem
 */
sealed interface LiteVaultItem : LiteItem