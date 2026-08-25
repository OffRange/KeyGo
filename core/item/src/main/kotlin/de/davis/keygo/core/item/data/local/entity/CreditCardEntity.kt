package de.davis.keygo.core.item.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
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
