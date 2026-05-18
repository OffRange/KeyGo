package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.ItemId

internal data class ItemTagProjection(
    val itemId: ItemId,
    val value: String,
)
