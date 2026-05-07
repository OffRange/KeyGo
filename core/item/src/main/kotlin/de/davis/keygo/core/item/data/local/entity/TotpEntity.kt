package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.SecretData

@Entity(
    tableName = "totp",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntity::class,
            parentColumns = ["id"],
            childColumns = ["password_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
internal data class TotpEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo(name = "password_id")
    val passwordId: ItemId,
    val secret: SecretData<String>,
    val issuer: String?,
    val accountName: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
)
