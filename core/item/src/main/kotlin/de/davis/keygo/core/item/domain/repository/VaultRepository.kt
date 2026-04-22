package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.ActiveVaultKeyInformation
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata

interface VaultRepository {
    suspend fun setActiveVault(vaultId: VaultId)
    suspend fun getActiveVaultKeyInformation(): ActiveVaultKeyInformation

    suspend fun getKeyInformation(vaultId: VaultId): KeyInformation?
    suspend fun createAndActivateVault(vault: Vault): Vault

    suspend fun deleteVault(vaultId: VaultId): Boolean

    suspend fun getAllVaultMetadata(): List<VaultMetadata>
}
