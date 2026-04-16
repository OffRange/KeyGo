package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.model.Vault

internal fun Vault.toData() = VaultEntity(
    id = id,
    name = name,
    keyInformation = keyInformation.toEntity(),
)

internal fun VaultEntity.toDomain() = Vault(
    id = id,
    name = name,
    keyInformation = keyInformation.toDomain(),
)
