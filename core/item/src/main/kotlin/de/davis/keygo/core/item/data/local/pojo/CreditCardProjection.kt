package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity

internal data class CreditCardProjection(
    @Embedded
    val creditCardEntity: CreditCardEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = ItemEntity::class,
    )
    val item: ItemProjection
)
