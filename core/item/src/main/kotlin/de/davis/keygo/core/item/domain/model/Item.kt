package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.processor.annotation.RootVaultEntity

@RootVaultEntity(name = "VaultItem")
sealed interface Item : LiteItem {
    override val id: ItemId
    val vaultId: VaultId
    override val name: String
    val keyInformation: KeyInformation
    val tags: Set<Tag>
    val note: String?
    val timestamp: Timestamp
}
