package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultContextRecord
import kotlinx.coroutines.flow.Flow

interface VaultContextRepository {

    suspend fun setVaultContext(context: VaultContext)
    suspend fun setLastInteractedVault(vaultId: VaultId)
    suspend fun setContextAndLastInteracted(vaultId: VaultId)

    fun observeVaultContext(): Flow<VaultContext>
    suspend fun getLastInteractedVaultId(): VaultId
    suspend fun getVaultContextRecord(): VaultContextRecord
}