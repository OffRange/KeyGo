package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.pojo.VaultMetadata
import de.davis.keygo.core.item.domain.model.VaultMetadata as DomainVaultMetadata

internal fun VaultMetadata.toDomain(): DomainVaultMetadata = DomainVaultMetadata(
    vaultId = vaultId,
    name = name,
    icon = icon
)

internal fun DomainVaultMetadata.toEntity(): VaultMetadata = VaultMetadata(
    vaultId = vaultId,
    name = name,
    icon = icon
)