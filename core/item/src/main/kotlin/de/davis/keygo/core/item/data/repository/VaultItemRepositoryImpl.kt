package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItem
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItemSearchResult
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class VaultItemRepositoryImpl(
    private val vaultDao: VaultDao
) : VaultItemRepository {

    override suspend fun deleteItem(itemId: ItemId) = vaultDao.delete(itemId)

    override suspend fun createOrUpdateVaultItem(item: VaultItem): ItemId =
        vaultDao.upsert(item.toData())

    override suspend fun getItemName(itemId: ItemId): String? = vaultDao.getNameById(itemId)

    override suspend fun doesNameExist(
        name: String,
        excludeId: ItemId?
    ): Boolean = vaultDao.existsName(name, excludeId)

    override suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType?
    ): List<LiteVaultItemSearchResult> = vaultDao.searchVaultItem(query, itemType)
        .map(LightweightVaultItemSearchResult::toDomain)

    override suspend fun setPinned(itemId: ItemId, pinned: Boolean) =
        vaultDao.setPinned(itemId, pinned)

    override fun observeLiteVaultItems(): Flow<List<LiteItem>> =
        vaultDao.observeLiteVaultItems().map {
            it.map(LightweightVaultItem::toDomain)
        }
}