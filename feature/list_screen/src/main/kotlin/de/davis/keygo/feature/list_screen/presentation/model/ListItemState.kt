package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem

data class ListItemState(
    val items: List<LiteItem> = emptyList(),
    val openedItemId: ItemId? = null,
    val selectedItemIds: Set<ItemId> = emptySet()
)