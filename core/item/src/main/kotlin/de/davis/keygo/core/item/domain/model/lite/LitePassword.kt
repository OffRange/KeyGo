package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo

data class LitePassword(
    override val vaultItemId: ItemId,
    val passwordId: ItemId,
    override val name: String,
    val username: String?,
    val domains: List<DomainInfo>,
) : LiteVaultItem