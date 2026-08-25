package de.davis.keygo.core.item.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
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
