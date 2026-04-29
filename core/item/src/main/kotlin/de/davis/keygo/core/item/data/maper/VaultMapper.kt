package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.model.Vault
import kotlin.time.Instant

internal fun Vault.toData() = VaultEntity(
    id = id,
    name = name,
    icon = icon,
    keyInformation = keyInformation.toEntity(),
    createdAt = createdAt.toEpochMilliseconds(),
)

internal fun VaultEntity.toDomain() = Vault(
    id = id,
    name = name,
    icon = icon,
    keyInformation = keyInformation.toDomain(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)
