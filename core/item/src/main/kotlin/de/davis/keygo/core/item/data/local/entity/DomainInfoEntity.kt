package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.davis.keygo.core.item.domain.alias.ItemId

@Entity(
    tableName = "domain_info",
    primaryKeys = ["login_id", "value"],
    foreignKeys = [
        ForeignKey(
            entity = LoginEntity::class,
            parentColumns = ["id"],
            childColumns = ["login_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("eTLD1")],
)
internal data class DomainInfoEntity(
    @ColumnInfo("login_id")
    val loginId: ItemId,
    val value: String,
    val eTLD1: String?,
)