package de.davis.keygo.core.data.repository

import de.davis.keygo.core.data.local.dao.VaultDao
import de.davis.keygo.core.data.mapper.toData
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.VaultItemRepository

class VaultItemRepositoryImpl(
    private val vaultDao: VaultDao
) : VaultItemRepository {

    override suspend fun createNewVaultItem(vaultItem: VaultItem): Long =
        vaultDao.insert(vaultItem.toData())
}