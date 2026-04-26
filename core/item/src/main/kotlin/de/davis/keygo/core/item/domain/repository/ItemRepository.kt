package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.flow.Flow

interface ItemRepository {

    suspend fun deleteItem(itemId: ItemId)

    suspend fun createOrUpdateVaultItem(item: Item): ItemId

    suspend fun getItemName(itemId: ItemId): String?
    suspend fun doesNameExist(
        name: String,
        excludeId: ItemId? = null,
        vaultId: VaultId? = null
    ): Boolean

    suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType? = null
    ): List<LiteItemSearchResult>

    suspend fun setPinned(itemId: ItemId, pinned: Boolean)

    fun observeLiteVaultItems(vaultId: VaultId? = null): Flow<List<LiteItem>>
}
