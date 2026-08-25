package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.ColumnInfo
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore

internal data class PasswordScoreProjection(
    val id: ItemId,
    @ColumnInfo(name = "password_score")
    val passwordScore: PasswordScore,
)
