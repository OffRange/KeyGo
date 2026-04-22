package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.ActiveVaultKeyInformation
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.repository.VaultRepository
import org.koin.core.annotation.Single

@Single
internal class VaultRepositoryImpl(
    private val vaultDao: VaultDao,
) : VaultRepository {
    override suspend fun setActiveVault(vaultId: VaultId) {
        vaultDao.setActive(vaultId)
    }

    override suspend fun getActiveVaultKeyInformation(): ActiveVaultKeyInformation =
        vaultDao.getActiveKeyInfo().toDomain()


    override suspend fun getKeyInformation(vaultId: VaultId): KeyInformation? =
        vaultDao.getKeyInfoById(vaultId)?.toDomain()

    override suspend fun createAndActivateVault(vault: Vault) = vault.also {
        vaultDao.insert(it.toData())
        vaultDao.setActive(it.id)
    }

    override suspend fun deleteVault(vaultId: VaultId): Boolean = vaultDao.deleteVault(vaultId)

    override suspend fun getAllVaultMetadata(): List<VaultMetadata> =
        vaultDao.getAllVaultMetadata().map { it.toDomain() }
}
