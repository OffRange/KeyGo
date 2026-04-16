package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

interface VaultRepository {
    suspend fun createVault(vault: Vault): Vault
    suspend fun getVault(id: VaultId): Vault?
}
