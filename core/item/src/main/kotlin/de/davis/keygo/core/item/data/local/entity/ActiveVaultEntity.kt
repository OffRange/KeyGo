package de.davis.keygo.core.item.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.VaultId

@Entity(
    tableName = "active_vault",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("vaultId")]
)
internal data class ActiveVaultEntity(
    @PrimaryKey
    val id: Long = 1L,
    val vaultId: VaultId
)
