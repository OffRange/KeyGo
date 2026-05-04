package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.data.local.pojo.MovableItemPojo
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.data.maper.toEntity
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
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class ItemRepositoryImpl(
    private val itemDao: ItemDao,
) : ItemRepository {

    override suspend fun deleteItem(itemId: ItemId) = itemDao.delete(itemId)

    override suspend fun createOrUpdateVaultItem(item: Item): ItemId {
        itemDao.upsert(item.toData())
        return item.id
    }

    override suspend fun getItemName(itemId: ItemId): String? = itemDao.getNameById(itemId)

    override suspend fun doesNameExist(
        name: String,
        excludeId: ItemId?,
        vaultId: VaultId?,
    ): Boolean = itemDao.existsName(name, excludeId, vaultId)

    override suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType?,
    ): List<LiteItemSearchResult> = itemDao.searchItem(query, itemType)
        .map(LightweightItemSearchResult::toDomain)

    override suspend fun setPinned(itemId: ItemId, pinned: Boolean) =
        itemDao.setPinned(itemId, pinned)

    override fun observeLiteVaultItems(vaultId: VaultId?): Flow<List<LiteItem>> =
        itemDao.observeLiteItems(vaultId).map { it.map(LightweightItem::toDomain) }

    override suspend fun getMovableItemsByVault(vaultId: VaultId): List<MovableItem> =
        itemDao.getMovableItemsByVault(vaultId).map { it.toDomain() }

    override suspend fun moveItemsToVault(
        items: List<MovableItem>,
        newVaultId: VaultId,
    ): Result<Unit, Throwable> = runCatching {
        itemDao.moveItemsToVault(
            items = items.map {
                MovableItemPojo(id = it.id, keyInformation = it.keyInformation.toEntity())
            },
            newVaultId = newVaultId,
        )
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(it) },
    )
}
