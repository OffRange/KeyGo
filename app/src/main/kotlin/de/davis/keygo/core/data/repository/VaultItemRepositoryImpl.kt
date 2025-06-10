package de.davis.keygo.core.data.repository

import de.davis.keygo.core.data.local.dao.VaultDao
import de.davis.keygo.core.data.mapper.toDomain
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.domain.repository.VaultItemRepository
import de.davis.keygo.core.domain.`typealias`.ItemId
import de.davis.keygo.generated.item.data.mapper.toData
import de.davis.keygo.generated.item.data.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class VaultItemRepositoryImpl(
    private val vaultDao: VaultDao
) : VaultItemRepository {

    override suspend fun deleteVaultItem(vaultItemId: ItemId) {
        vaultDao.delete(vaultItemId)
    }

    override suspend fun createNewVaultItem(vaultItem: VaultItem): ItemId =
        vaultDao.insert(vaultItem.toData())

    override suspend fun searchVaultItem(query: String): List<VaultSearchResult> =
        vaultDao.searchVaultItem(query).map { it.toDomain() }

    override fun observeVaultItems(): Flow<List<VaultItem>> =
        vaultDao.getAllVaultItems()
            .map { vaultItems -> vaultItems.map { it.toDomain() } }
}