package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem

@Immutable
internal data class ItemSelection(val pinnedById: Map<ItemId, Boolean> = emptyMap()) {

    val ids: Set<ItemId> get() = pinnedById.keys

    val isActive: Boolean get() = pinnedById.isNotEmpty()

    val allPinned: Boolean get() = isActive && pinnedById.values.all { it }

    fun select(itemId: ItemId, pinned: Boolean): ItemSelection =
        ItemSelection(pinnedById + (itemId to pinned))

    fun deselect(itemId: ItemId): ItemSelection = ItemSelection(pinnedById - itemId)

    fun withAllPinned(pinned: Boolean): ItemSelection =
        ItemSelection(pinnedById.mapValues { pinned })

    companion object {
        fun of(items: List<LiteItem>): ItemSelection =
            ItemSelection(items.associate { it.id to it.pinned })
    }
}
