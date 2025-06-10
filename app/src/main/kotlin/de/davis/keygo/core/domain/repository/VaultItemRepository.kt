package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import kotlinx.coroutines.flow.Flow

interface VaultItemRepository {

    suspend fun deleteVaultItem(vaultItemId: ItemId)
    suspend fun createNewVaultItem(vaultItem: VaultItem): ItemId
    suspend fun searchVaultItem(query: String): List<VaultSearchResult>
    fun observeVaultItems(): Flow<List<VaultItem>>
}