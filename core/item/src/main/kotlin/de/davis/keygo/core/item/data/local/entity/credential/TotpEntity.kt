package de.davis.keygo.core.item.data.local.entity.credential

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload

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
    @Embedded(prefix = "secret_")
    val secret: EncryptedPayload,
    val issuer: String?,
    @ColumnInfo(name = "account_name")
    val accountName: String?,
    val algorithm: String,
    val digits: Int,
    val period: Int,
)
