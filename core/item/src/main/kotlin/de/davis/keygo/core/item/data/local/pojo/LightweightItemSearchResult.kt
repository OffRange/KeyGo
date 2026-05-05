package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

internal data class LightweightItemSearchResult(
    val id: ItemId,
    val name: String,
    val itemType: VaultItemType,
    val matchedName: Boolean,
    val matchedNote: Boolean,
    val pinned: Boolean
)
