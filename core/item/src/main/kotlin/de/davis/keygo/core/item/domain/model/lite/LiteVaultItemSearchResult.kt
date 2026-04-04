package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

data class LiteVaultItemSearchResult(
    override val vaultItemId: ItemId,
    override val name: String,
    override val itemType: VaultItemType,
    val matchedName: Boolean,
    val matchedNote: Boolean,
) : LiteItem
