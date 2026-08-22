package de.davis.keygo.feature.list_screen.presentation

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem

internal fun List<LiteItem>.withSuggestedFirst(suggestedItemIds: Set<ItemId>): List<LiteItem> {
    if (suggestedItemIds.isEmpty()) return this

    val (suggested, rest) = partition { it.id in suggestedItemIds }
    return if (suggested.isEmpty()) this else suggested + rest
}
