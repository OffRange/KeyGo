package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [ItemRepository] for tests, layered on top of a [FakePasswordRepository] so the
 * password store is the single source of truth for vault/key state.
 */
class FakeItemRepository(
    private val passwordRepository: FakePasswordRepository,
) : ItemRepository {

    /**
     * If non-null, [moveItem] fails when called with this id.
     * Persists across calls; the consumer clears it explicitly.
     */
    var failMoveForId: Pair<ItemId, Throwable>? = null

    override suspend fun deleteItem(itemId: ItemId) = Unit

    override suspend fun createOrUpdateVaultItem(item: Item): ItemId = item.id

    override suspend fun getItemName(itemId: ItemId): String? =
        passwordRepository.getPasswordById(itemId)?.name

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
        passwordRepository.getPasswordsByVault(vaultId)
            .map { MovableItem(id = it.id, keyInformation = it.keyInformation) }

    override suspend fun moveItem(
        itemId: ItemId,
        newVaultId: VaultId,
        newKeyInformation: KeyInformation,
    ): Result<Unit, Throwable> {
        failMoveForId?.let { (id, error) ->
            if (id == itemId) return Result.Failure(error)
        }
        val existing = passwordRepository.getPasswordById(itemId)
            ?: return Result.Failure(NoSuchElementException("No item with id $itemId"))
        passwordRepository.seed(
            existing.copy(vaultId = newVaultId, keyInformation = newKeyInformation)
        )
        return Result.Success(Unit)
    }
}
