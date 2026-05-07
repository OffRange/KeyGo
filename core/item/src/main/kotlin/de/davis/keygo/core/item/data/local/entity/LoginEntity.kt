package de.davis.keygo.core.item.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId

@Entity(
    tableName = "login",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
internal data class LoginEntity(
    @PrimaryKey
    val id: ItemId,
    val username: String?,
)
