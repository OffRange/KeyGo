package de.davis.keygo.core.item.data.local.entity.credential

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
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
