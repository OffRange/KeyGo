package de.davis.keygo.core.item.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData

// The primary key is shared with ItemEntity — one ItemId identifies both the base item row and
// this password row. This models the "is-a" relationship at the DB level (joined-table
// inheritance): there is no separate password-specific ID.
@Entity(
    tableName = "password",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
internal data class PasswordEntity(
    @PrimaryKey
    val id: ItemId,
    val username: String?,
    val score: Password.Score,
    val password: SecretData<String>,
)
