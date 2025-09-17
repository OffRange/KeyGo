package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId

data class LiteVaultItemSearchResult(
    override val vaultItemId: ItemId,
    override val name: String,
    val matchedName: Boolean,
    val matchedNote: Boolean
) : LiteItem
