package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.flow.Flow

interface VaultItemRepository {

    suspend fun deleteItem(itemId: ItemId)

    suspend fun createOrUpdateVaultItem(item: VaultItem): ItemId

    suspend fun getItemName(itemId: ItemId): String?
    suspend fun doesNameExist(name: String, excludeId: ItemId? = null): Boolean

    suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType? = null
    ): List<LiteVaultItemSearchResult>

    suspend fun setPinned(itemId: ItemId, pinned: Boolean)

    fun observeLiteVaultItems(): Flow<List<LiteItem>>
}
