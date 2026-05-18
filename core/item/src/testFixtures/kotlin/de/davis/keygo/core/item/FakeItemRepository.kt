package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory [ItemRepository] for tests.
 *
 * Call [seedEnvelope] to register an [ItemKeyEnvelope] for a specific item. If no envelope has
 * been seeded, [getItemKeyEnvelope] returns a fallback with blank vault-key bytes.
 * Tests that exercise real key-derivation paths must seed explicit envelopes with accurate vault
 * key info.
 */
class FakeItemRepository(
    private val loginRepository: FakeLoginRepository = FakeLoginRepository(),
) : ItemRepository {

    private val allStores = loginRepository.store
    private val envelopes = mutableMapOf<ItemId, ItemKeyEnvelope>()

    fun seedEnvelope(envelope: ItemKeyEnvelope) {
        envelopes[envelope.itemId] = envelope
    }

    /**
     * If non-null, [moveItemsToVault] fails the whole bulk write when any of the supplied
     * items has this id. Models a SQLite transaction failing and rolling back.
     * Persists across calls; the consumer clears it explicitly.
     */
    var failMoveForId: Pair<ItemId, Throwable>? = null

    override fun observeAllTags(): Flow<List<Tag>> =
        allStores.map { store -> store.values.flatMap { item -> item.tags }.distinct() }

    override fun observeItemIdsForTags(labels: Set<String>): Flow<Set<ItemId>> =
        allStores.map { store ->
            store.values
                .filter { item -> item.tags.any { it in labels } }
                .map { it.id }
                .toSet()
        }

    override fun observeTagsByItem(): Flow<Map<ItemId, Set<Tag>>> =
        allStores.map { store ->
            store.values
                .filter { item -> item.tags.isNotEmpty() }
                .associate { item -> item.id to item.tags.toSet() }
        }

    override suspend fun deleteItem(itemId: ItemId) = Unit

    override suspend fun createOrUpdateVaultItem(item: Item): ItemId = item.id

    override suspend fun getItemName(itemId: ItemId): String? =
        allStores.value[itemId]?.name

    override suspend fun doesNameExist(
        name: String,
        excludeId: ItemId?,
        vaultId: VaultId?,
    ): Boolean = allStores.value.values.any {
        it.name == name &&
                (excludeId == null || it.id != excludeId) &&
                (vaultId == null || it.vaultId == vaultId)
    }

    override suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType?,
    ): List<LiteItemSearchResult> = emptyList()

    override suspend fun setPinned(itemId: ItemId, pinned: Boolean) = Unit

    override fun observeLiteVaultItems(vaultId: VaultId?): Flow<List<LiteItem>> =
        flowOf(emptyList())

    override suspend fun getItemKeyEnvelope(itemId: ItemId): ItemKeyEnvelope? = envelopes[itemId]

    override suspend fun getMovableItemsByVault(vaultId: VaultId): List<MovableItem> =
        allStores.value.values.filter { it.vaultId == vaultId }.map { item ->
            MovableItem(
                id = item.id,
                keyInformation = KeyInformation(
                    wrappedKey = item.keyInformation.wrappedKey,
                    keyNonce = item.keyInformation.keyNonce,
                )
            )
        }

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
