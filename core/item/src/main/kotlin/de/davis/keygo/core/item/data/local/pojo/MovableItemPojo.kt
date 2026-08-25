package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.Embedded
import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.domain.alias.ItemId

internal data class MovableItemPojo(
    val id: ItemId,
    @Embedded
    val keyInformation: KeyInformation,
)
