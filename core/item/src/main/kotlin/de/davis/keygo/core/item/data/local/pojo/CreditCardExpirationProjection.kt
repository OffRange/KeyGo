package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.ColumnInfo
import de.davis.keygo.core.item.domain.alias.ItemId
import java.time.YearMonth

internal data class CreditCardExpirationProjection(
    val id: ItemId,
    @ColumnInfo(name = "expiration_date")
    val expirationDate: YearMonth,
)
