package de.davis.keygo.core.item.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
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