package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    suspend fun createVault(vault: Vault)
    suspend fun deleteVault(vaultId: VaultId)
    fun observeAllVaultMetadata(): Flow<List<VaultMetadata>>
    
    suspend fun getKeyInformation(vaultId: VaultId): KeyInformation?
}
