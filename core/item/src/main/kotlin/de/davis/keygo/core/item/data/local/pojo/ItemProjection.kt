package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.TagCrossRef
import de.davis.keygo.core.item.data.local.entity.TagEntity

internal data class ItemProjection(
    @Embedded
    val itemEntity: ItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TagCrossRef::class,
            parentColumn = "item_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: Set<TagEntity> = emptySet(),
)
