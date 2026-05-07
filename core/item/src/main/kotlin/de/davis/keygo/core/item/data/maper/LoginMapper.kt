package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.lite.LiteLogin

internal fun Login.toData(): PasswordEntity = PasswordEntity(
    loginId = id,
    passwordScore = passwordScore,
    password = password,
)

internal fun LoginProjection.toDomain(): Login = Login(
    id = loginEntity.id,
    username = loginEntity.username,
    passwordScore = passwordEntity.passwordScore,
    totp = totp?.toDomain(),
    password = passwordEntity.password,

    passkeyRPs = rpEntity.map { it.rp }.toSet(),

    domainInfos = domains.map(DomainInfoEntity::toDomain).toSet(),

    vaultId = itemEntity.vaultId,
    name = itemEntity.name,
    note = itemEntity.note,
    keyInformation = itemEntity.keyInformation.toDomain(),
    pinned = itemEntity.pinned,
)

internal fun LightweightLogin.toDomain(): LiteLogin = LiteLogin(
    id = id,
    name = name,
    pinned = pinned,
    username = username,
    domains = domains.map(DomainInfoEntity::toDomain),
)
