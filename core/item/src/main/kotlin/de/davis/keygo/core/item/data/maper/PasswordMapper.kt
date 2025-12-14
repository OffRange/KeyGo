package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword

internal fun Password.toData(vaultItemId: ItemId = this.vaultItemId): PasswordEntity =
    PasswordEntity(
        id = id,
        username = username,
        score = score,
        totpSecret = totpSecret,
        vaultItemId = vaultItemId
    )

internal fun VaultPassword.toDomain(): Password = Password(
    id = passwordEntity.id,
    username = passwordEntity.username,
    score = passwordEntity.score,
    totpSecret = passwordEntity.totpSecret,

    passkeyRPs = rpEntity.map { it.rp }.toSet(),

    domainInfos = domains.map(DomainInfoEntity::toDomain).toSet(),

    vaultItemId = vaultItemEntity.id,
    name = vaultItemEntity.name,
    note = vaultItemEntity.note,
    encryptedData = vaultItemEntity.encryptedData
)

internal fun LightweightPassword.toDomain(): LitePassword = LitePassword(
    vaultItemId = vaultItemId,
    passwordId = passwordId,
    name = name,
    username = username,
    domains = domains.map(DomainInfoEntity::toDomain)
)