package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.entity.VaultItemEntity

internal data class VaultPassword(
    @Embedded
    val passwordEntity: PasswordEntity,

    @Relation(
        parentColumn = "vault_item_id",
        entityColumn = "id",
    )
    val vaultItemEntity: VaultItemEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "password_id",
        entity = PasskeyEntity::class
    )
    val rpEntity: List<RP>,

    @Relation(
        parentColumn = "id",
        entityColumn = "password_id",
    )
    val domains: List<DomainInfoEntity>
)
