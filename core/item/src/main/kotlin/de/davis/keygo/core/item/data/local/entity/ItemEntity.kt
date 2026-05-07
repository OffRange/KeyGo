package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("vault_id")],
)
internal data class ItemEntity(
    @PrimaryKey
    val id: ItemId,
    @ColumnInfo(name = "vault_id")
    val vaultId: VaultId,

    val name: String,
    val note: String?,
    @ColumnInfo(name = "item_type")
    val itemType: VaultItemType,
    val pinned: Boolean,

    @Embedded val keyInformation: KeyInformation,
)
