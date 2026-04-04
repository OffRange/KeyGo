package de.davis.keygo.core.item.data.local.pojo

import androidx.room.ColumnInfo
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password

internal data class PasswordScoreEntry(
    @ColumnInfo(name = "vault_item_id")
    val vaultItemId: ItemId,
    val score: Password.Score,
)
