package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.domain.`typealias`.ItemId
import kotlinx.coroutines.flow.Flow

interface VaultItemRepository {

    suspend fun deleteVaultItem(vaultItemId: ItemId)
    suspend fun createNewVaultItem(vaultItem: VaultItem): ItemId
    suspend fun searchVaultItem(query: String): List<VaultSearchResult>
    fun observeVaultItems(): Flow<List<VaultItem>>
}