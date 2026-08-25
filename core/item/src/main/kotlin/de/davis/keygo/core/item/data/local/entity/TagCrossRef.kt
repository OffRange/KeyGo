package de.davis.keygo.core.item.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import de.davis.keygo.core.item.domain.alias.ItemId

@Entity(
    tableName = "tag_cross_ref",
    primaryKeys = ["item_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
internal data class TagCrossRef(
    @ColumnInfo(name = "item_id")
    val itemId: ItemId,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)
