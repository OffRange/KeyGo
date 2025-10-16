package de.davis.keygo.core.item.data.local.pojo

import androidx.room.ColumnInfo
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId

internal data class LightweightPassword(
    @ColumnInfo("vault_item_id")
    val vaultItemId: ItemId,
    @ColumnInfo("password_id")
    val passwordId: ItemId,
    val username: String?,
    val name: String,

    @Relation(
        parentColumn = "password_id",
        entityColumn = "password_id"
    )
    val domains: List<DomainInfoEntity>
)