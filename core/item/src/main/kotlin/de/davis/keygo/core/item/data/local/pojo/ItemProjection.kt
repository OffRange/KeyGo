package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.TagCrossRef
import de.davis.keygo.core.item.data.local.entity.TagEntity

internal data class ItemProjection(
    @Embedded
    val itemEntity: ItemEntity,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(
            value = TagCrossRef::class,
            parentColumns = ["item_id"],
            entityColumns = ["tag_id"],
        ),
    )
    val tags: Set<TagEntity> = emptySet(),
)
