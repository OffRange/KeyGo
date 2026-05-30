package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import java.time.YearMonth

@Entity(
    tableName = "credit_card",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
internal data class CreditCardEntity(
    @PrimaryKey
    val id: ItemId,
    val holder: String?,
    @Embedded(prefix = "card_number_")
    val cardNumber: EncryptedPayload?,
    @Embedded(prefix = "cvv_")
    val cvv: EncryptedPayload?,
    @ColumnInfo(name = "expiration_date")
    val expirationDate: YearMonth?
)
