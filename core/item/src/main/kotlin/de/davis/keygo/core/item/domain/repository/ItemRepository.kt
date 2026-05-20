package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.Result
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

    fun observeAllTags(): Flow<List<Tag>>

    fun observeItemIdsForTags(tags: Set<Tag>): Flow<Set<ItemId>>

    fun observeTagsByItem(): Flow<Map<ItemId, Set<Tag>>>

    fun observeLiteVaultItems(vaultId: VaultId? = null): Flow<List<LiteItem>>

    suspend fun getItemKeyEnvelope(itemId: ItemId): ItemKeyEnvelope?

    suspend fun getMovableItemsByVault(vaultId: VaultId): List<MovableItem>

    /**
     * Atomically moves all [items] to [newVaultId], updating each item's `vault_id` and
     * key information in a single SQLite transaction. Either every row is updated or none are.
     */
    suspend fun moveItemsToVault(
        items: List<MovableItem>,
        newVaultId: VaultId,
    ): Result<Unit, Throwable>
}
