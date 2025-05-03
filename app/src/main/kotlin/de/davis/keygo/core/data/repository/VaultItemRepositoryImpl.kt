package de.davis.keygo.core.data.repository

import de.davis.keygo.core.data.local.dao.VaultDao
import de.davis.keygo.core.data.mapper.toData
import de.davis.keygo.core.data.mapper.toDomain
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.VaultItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VaultItemRepositoryImpl(
    private val vaultDao: VaultDao
) : VaultItemRepository {

    override suspend fun deleteVaultItem(vaultItemId: Long) {
        vaultDao.delete(vaultItemId)
    }

    override suspend fun createNewVaultItem(vaultItem: VaultItem): Long =
        vaultDao.insert(vaultItem.toData())

    override fun observeVaultItems(): Flow<List<VaultItem>> =
        vaultDao.getAllVaultItems()
            .map { vaultItems -> vaultItems.map { it.toDomain() } }
}