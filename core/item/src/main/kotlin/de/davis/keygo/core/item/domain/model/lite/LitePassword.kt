package de.davis.keygo.core.item.domain.model.lite

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

data class LitePassword(
    override val id: ItemId,
    override val name: String,
    override val pinned: Boolean,
    val username: String?,
    val domains: List<DomainInfo>,
) : LiteVaultItem {
    override val itemType: VaultItemType = VaultItemType.Password
}
