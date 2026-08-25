package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.Embedded
import androidx.room3.Relation
import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity

internal data class CreditCardProjection(
    @Embedded
    val creditCardEntity: CreditCardEntity,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        entity = ItemEntity::class,
    )
    val item: ItemProjection
)
