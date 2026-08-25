package de.davis.keygo.core.item.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

@Entity(tableName = "vault")
internal data class VaultEntity(
    @PrimaryKey
    val id: VaultId,
    val name: String,
    val icon: Vault.Icon,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @Embedded
    val keyInformation: KeyInformation,
)
