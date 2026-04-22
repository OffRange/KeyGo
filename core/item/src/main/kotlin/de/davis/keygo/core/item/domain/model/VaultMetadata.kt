package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

data class VaultMetadata(
    val vaultId: VaultId,
    val name: String,
    val icon: Vault.Icon,
)
