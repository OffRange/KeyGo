package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.VaultRepository
import org.koin.core.annotation.Single

@Single
internal class VaultRepositoryImpl(
    private val vaultDao: VaultDao,
) : VaultRepository {

    override suspend fun createVault(vault: Vault) = vault.also {
        vaultDao.upsert(it.toData())
    }

    override suspend fun getVault(id: VaultId): Vault? = vaultDao.getById(id)?.toDomain()
}
