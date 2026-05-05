package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword

internal fun Password.toData(): PasswordEntity = PasswordEntity(
    id = id,
    username = username,
    score = score,
    password = password,
    totpSecret = totpSecret,
)

internal fun VaultPassword.toDomain(): Password = Password(
    id = passwordEntity.id,
    username = passwordEntity.username,
    score = passwordEntity.score,
    totpSecret = passwordEntity.totpSecret,
    password = passwordEntity.password,

    passkeyRPs = rpEntity.map { it.rp }.toSet(),

    domainInfos = domains.map(DomainInfoEntity::toDomain).toSet(),

    vaultId = itemEntity.vaultId,
    name = itemEntity.name,
    note = itemEntity.note,
    keyInformation = itemEntity.keyInformation.toDomain(),
    pinned = itemEntity.pinned,
)

internal fun LightweightPassword.toDomain(): LitePassword = LitePassword(
    id = id,
    name = name,
    pinned = pinned,
    username = username,
    domains = domains.map(DomainInfoEntity::toDomain),
)
