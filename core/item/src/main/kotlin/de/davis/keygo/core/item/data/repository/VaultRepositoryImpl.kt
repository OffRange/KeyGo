package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.VaultUpdater
import de.davis.keygo.core.item.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class VaultRepositoryImpl(
    private val vaultDao: VaultDao,
) : VaultRepository {
    override suspend fun createVault(vault: Vault) {
        vaultDao.insert(vault.toData())
    }

    override suspend fun updateVault(vault: VaultUpdater) {
        vaultDao.update(vault.toData())
    }

    override suspend fun deleteVault(vaultId: VaultId) = vaultDao.delete(vaultId)

    override fun observeAllVaultMetadata(): Flow<List<VaultMetadata>> =
        vaultDao.observeAllVaultMetadata().map { list -> list.map { it.toDomain() } }

    override suspend fun getVaultMetadata(vaultId: VaultId): VaultMetadata? =
        vaultDao.getVaultMetadata(vaultId)?.toDomain()

    override suspend fun getKeyInformation(vaultId: VaultId): KeyInformation? =
        vaultDao.getKeyInfoById(vaultId)?.toDomain()

    override suspend fun getLastCreatedVaultId(exclude: VaultId): VaultId? =
        vaultDao.lastCreatedVaultId(exclude)
}
