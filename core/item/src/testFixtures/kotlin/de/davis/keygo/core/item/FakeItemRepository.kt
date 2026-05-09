package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [ItemRepository] for tests, layered on top of a [FakeLoginRepository] so the
 * login store is the single source of truth for vault/key state.
 */
class FakeItemRepository(
    private val loginRepository: FakeLoginRepository,
) : ItemRepository {

    /**
     * If non-null, [moveItemsToVault] fails the whole bulk write when any of the supplied
     * items has this id. Models a SQLite transaction failing and rolling back.
     * Persists across calls; the consumer clears it explicitly.
     */
    var failMoveForId: Pair<ItemId, Throwable>? = null

    override suspend fun deleteItem(itemId: ItemId) = Unit

    override suspend fun createOrUpdateVaultItem(item: Item): ItemId = item.id

    override suspend fun getItemName(itemId: ItemId): String? =
        loginRepository.getLoginById(itemId)?.name

    override suspend fun doesNameExist(
        name: String,
        excludeId: ItemId?,
        vaultId: VaultId?,
    ): Boolean = false

    override suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType?,
    ): List<LiteItemSearchResult> = emptyList()

    override suspend fun setPinned(itemId: ItemId, pinned: Boolean) = Unit

    override fun observeLiteVaultItems(vaultId: VaultId?): Flow<List<LiteItem>> =
        flowOf(emptyList())

    override suspend fun getMovableItemsByVault(vaultId: VaultId): List<MovableItem> =
        loginRepository.getLoginsByVault(vaultId)
            .map { MovableItem(id = it.id, keyInformation = it.keyInformation) }

    override suspend fun moveItemsToVault(
        items: List<MovableItem>,
        newVaultId: VaultId,
    ): Result<Unit, Throwable> {
        failMoveForId?.let { (failId, error) ->
            if (items.any { it.id == failId }) return Result.Failure(error)
        }
        val updates = items.map { item ->
            val existing = loginRepository.getLoginById(item.id)
                ?: return Result.Failure(NoSuchElementException("No item with id ${item.id}"))
            existing.copy(vaultId = newVaultId, keyInformation = item.keyInformation)
        }
        updates.forEach { loginRepository.seed(it) }
        return Result.Success(Unit)
    }
}
