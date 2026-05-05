package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class MovableItem(
    val id: ItemId,
    val keyInformation: KeyInformation,
)
