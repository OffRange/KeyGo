package de.davis.keygo.core.item.data.local.entity.credential

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.PasswordScore

// The primary key is shared with ItemEntity: one ItemId identifies both the base item row and
// this password row. This models the "is-a" relationship at the DB level (joined-table
// inheritance): there is no separate password-specific ID.
@Entity(
    tableName = "password",
    foreignKeys = [
        ForeignKey(
            entity = LoginEntity::class,
            parentColumns = ["id"],
            childColumns = ["login_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
internal data class PasswordEntity(
    @PrimaryKey
    @ColumnInfo(name = "login_id")
    val loginId: ItemId,
    @ColumnInfo(name = "password_score")
    val passwordScore: PasswordScore,
    @Embedded(prefix = "password_")
    val password: EncryptedPayload,
)
