package de.davis.keygo.core.item.data.local.pojo

import androidx.room.ColumnInfo
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId

internal data class LightweightLogin(
    val id: ItemId,
    val username: String?,
    val name: String,
    val pinned: Boolean,
    @ColumnInfo(name = "has_password")
    val hasPassword: Boolean,

    @Relation(
        parentColumn = "id",
        entityColumn = "login_id",
    )
    val domains: List<DomainInfoEntity>,
)
