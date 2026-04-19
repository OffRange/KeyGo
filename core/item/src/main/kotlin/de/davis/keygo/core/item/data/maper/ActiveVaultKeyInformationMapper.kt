package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.pojo.ActiveVaultKeyInformation
import de.davis.keygo.core.item.domain.model.ActiveVaultKeyInformation as DomainActiveVaultKeyInformation


internal fun DomainActiveVaultKeyInformation.toEntity(): ActiveVaultKeyInformation =
    ActiveVaultKeyInformation(
        keyInformation = keyInformation.toEntity(),
        vaultId = vaultId
    )

internal fun ActiveVaultKeyInformation.toDomain(): DomainActiveVaultKeyInformation =
    DomainActiveVaultKeyInformation(
        keyInformation = keyInformation.toDomain(),
        vaultId = vaultId
    )