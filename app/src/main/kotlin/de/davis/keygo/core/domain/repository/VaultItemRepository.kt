package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.VaultItem

interface VaultItemRepository {

    suspend fun createNewVaultItem(vaultItem: VaultItem): Long
}