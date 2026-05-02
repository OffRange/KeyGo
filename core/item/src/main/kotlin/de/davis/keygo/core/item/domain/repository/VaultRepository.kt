package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.VaultUpdater
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    suspend fun createVault(vault: Vault)
    suspend fun updateVault(vault: VaultUpdater)
    suspend fun deleteVault(vaultId: VaultId)
    fun observeAllVaultMetadata(): Flow<List<VaultMetadata>>
    suspend fun getVaultMetadata(vaultId: VaultId): VaultMetadata?

    suspend fun getKeyInformation(vaultId: VaultId): KeyInformation?

    suspend fun getLastCreatedVaultId(exclude: VaultId): VaultId?
}
