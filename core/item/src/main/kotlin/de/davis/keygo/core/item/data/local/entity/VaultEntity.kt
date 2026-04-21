package de.davis.keygo.core.item.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

@Entity(tableName = "vault")
internal data class VaultEntity(
    @PrimaryKey
    val id: VaultId,
    val name: String,
    val icon: Vault.Icon,

    @Embedded
    val keyInformation: KeyInformation,
)
