package de.davis.keygo.core.data.local.model

import androidx.room.Embedded
import androidx.room.Relation

data class VaultPassword(
    @Embedded
    val vaultItem: VaultItem,
    @Relation(
        parentColumn = "id",
        entityColumn = "vault_id",
    )
    val password: Password,
)
