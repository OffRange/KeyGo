package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
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

    override suspend fun moveItem(
        itemId: ItemId,
        newVaultId: VaultId,
        newKeyInformation: KeyInformation,
    ): Result<Unit, Throwable> = runCatching {
        itemDao.moveItem(
            id = itemId,
            newVaultId = newVaultId,
            wrappedKey = newKeyInformation.wrappedKey,
            keyNonce = newKeyInformation.keyNonce,
        )
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(it) },
    )
}
