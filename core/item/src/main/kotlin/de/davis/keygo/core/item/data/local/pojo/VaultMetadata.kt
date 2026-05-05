package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

internal data class VaultMetadata(
    val vaultId: VaultId,
    val name: String,
    val icon: Vault.Icon,
    val count: Int,
    val createdAt: Long,
)
