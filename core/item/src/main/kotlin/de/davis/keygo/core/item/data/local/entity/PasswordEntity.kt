package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vault_item_id"], unique = true)
    ],
)
internal data class PasswordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: ItemId,
    val username: String?,
    val score: Password.Score,
    @ColumnInfo(name = "totp_secret")
    val totpSecret: SecretData<String>?,
    @ColumnInfo(name = "vault_item_id")
    val vaultItemId: ItemId,
)
