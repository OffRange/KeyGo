package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

/**
 * A lightweight representation of a item stored in the vault.
 */
interface LiteItem {
    val id: ItemId
    val name: String

    val itemType: VaultItemType
    val pinned: Boolean

    @ConsistentCopyVisibility
    data class Concrete internal constructor(
        override val id: ItemId,
        override val name: String,
        override val itemType: VaultItemType,
        override val pinned: Boolean
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