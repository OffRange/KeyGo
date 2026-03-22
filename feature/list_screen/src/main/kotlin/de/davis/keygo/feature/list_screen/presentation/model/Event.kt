package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId

internal sealed interface Event {

    data class ItemSelected(val itemId: ItemId) : Event
    data class ItemLongClicked(val itemId: ItemId) : Event
}