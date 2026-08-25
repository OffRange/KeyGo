package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.Embedded
import androidx.room3.Relation
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
        parentColumns = ["id"],
        entityColumns = ["id"],
        entity = ItemEntity::class,
    )
    val item: ItemProjection,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["login_id"],
    )
    val passwordEntity: PasswordEntity?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["login_id"],
        entity = PasskeyEntity::class
    )
    val passkeys: List<PasskeyRefPojo>,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["login_id"],
    )
    val domains: List<DomainInfoEntity>,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["login_id"],
    )
    val totp: TotpEntity?,
)
