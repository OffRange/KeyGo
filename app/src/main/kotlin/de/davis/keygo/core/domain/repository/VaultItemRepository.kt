package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import kotlinx.coroutines.flow.Flow

interface VaultItemRepository {

    suspend fun deleteVaultItem(vaultItemId: Long)
    suspend fun createNewVaultItem(vaultItem: VaultItem): Long
    suspend fun searchVaultItem(query: String): List<VaultSearchResult>
    fun observeVaultItems(): Flow<List<VaultItem>>
}