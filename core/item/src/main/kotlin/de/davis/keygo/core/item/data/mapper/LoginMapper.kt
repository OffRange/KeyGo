package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.lite.LiteLogin

internal fun Login.toLoginEntity(): LoginEntity = LoginEntity(
    id = id,
    username = username,
)

internal fun Login.toPasswordEntity(): PasswordEntity? =
    passwordCredential?.let {
        PasswordEntity(
            loginId = id,
            passwordScore = it.score,
            password = it.secret.payload,
        )
    }

internal fun LoginProjection.toDomain(): Login = Login(
    id = loginEntity.id,
    username = loginEntity.username,
    passwordCredential = passwordEntity?.let {
        PasswordCredential(
            secret = PasswordSecret(it.password),
            score = it.passwordScore,
        )
    },
    totp = totp?.toDomain(),
    passkeyRPs = rpEntity.map { it.rp }.toSet(),
    domainInfos = domains.map(DomainInfoEntity::toDomain).toSet(),
    vaultId = item.itemEntity.vaultId,
    name = item.itemEntity.name,
    note = item.itemEntity.note,
    keyInformation = item.itemEntity.keyInformation.toDomain(),
    tags = item.tags.map(TagEntity::toDomain).toSet(),
    pinned = item.itemEntity.pinned,
)

internal fun LightweightLogin.toDomain(): LiteLogin = LiteLogin(
    id = id,
    name = name,
    pinned = pinned,
    username = username,
    hasPassword = hasPassword,
    domains = domains.map(DomainInfoEntity::toDomain),
)
