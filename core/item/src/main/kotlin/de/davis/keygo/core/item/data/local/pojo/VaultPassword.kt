package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity

internal data class VaultPassword(
    @Embedded
    val passwordEntity: PasswordEntity,

    // PasswordEntity.id == ItemEntity.id (shared primary key — "is-a" relationship).
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
    )
    val itemEntity: ItemEntity,

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
