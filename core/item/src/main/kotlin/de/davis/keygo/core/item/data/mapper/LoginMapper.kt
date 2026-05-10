package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.lite.LiteLogin

internal fun Login.toLoginEntity(): LoginEntity = LoginEntity(
    id = id,
    username = username,
)

internal fun Login.toPasswordEntity(): PasswordEntity = PasswordEntity(
    loginId = id,
    passwordScore = passwordScore,
    password = password.payload,
)

internal fun LoginProjection.toDomain(): Login = Login(
    id = loginEntity.id,
    username = loginEntity.username,
    passwordScore = passwordEntity.passwordScore,
    totp = totp?.toDomain(),
    password = PasswordSecret(passwordEntity.password),

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
