package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

data class VaultUpdater(
    val id: VaultId,
    val name: String,
    val icon: Vault.Icon,
)
