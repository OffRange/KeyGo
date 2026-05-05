package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

internal data class VaultUpdater(
    val id: VaultId,
    val name: String,
    val icon: Vault.Icon,
)
