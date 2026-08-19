package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import androidx.room.Relation
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity

internal data class LoginProjection(

    @Embedded
    val loginEntity: LoginEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = ItemEntity::class,
    )
    val item: ItemProjection,

    @Relation(
        parentColumn = "id",
        entityColumn = "login_id",
    )
    val passwordEntity: PasswordEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "login_id",
        entity = PasskeyEntity::class
    )
    val passkeys: List<PasskeyRefPojo>,

    @Relation(
        parentColumn = "id",
        entityColumn = "login_id",
    )
    val domains: List<DomainInfoEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "login_id",
    )
    val totp: TotpEntity?,
)
