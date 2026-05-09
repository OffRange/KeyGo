package de.davis.keygo.core.item.data.local.entity.credential

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.SecretData

@Entity(
    tableName = "totp",
    foreignKeys = [
        ForeignKey(
            entity = LoginEntity::class,
            parentColumns = ["id"],
            childColumns = ["login_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
internal data class TotpEntity(
    @PrimaryKey
    @ColumnInfo(name = "login_id")
    val loginId: ItemId,
    val secret: SecretData<String>,
    val issuer: String?,
    @ColumnInfo(name = "account_name")
    val accountName: String?,
    val algorithm: String,
    val digits: Int,
    val period: Int,
)
