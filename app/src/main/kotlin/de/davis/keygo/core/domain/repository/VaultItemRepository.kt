package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.VaultItem
import kotlinx.coroutines.flow.Flow

interface VaultItemRepository {

    suspend fun deleteVaultItem(vaultItemId: Long)
    suspend fun createNewVaultItem(vaultItem: VaultItem): Long
    fun observeVaultItems(): Flow<List<VaultItem>>
}