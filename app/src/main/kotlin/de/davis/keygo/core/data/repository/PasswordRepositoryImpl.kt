package de.davis.keygo.core.data.repository

import de.davis.keygo.core.data.local.dao.PasswordDao
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.generated.item.data.local.relation.VaultPassword
import de.davis.keygo.generated.item.data.mapper.toData
import de.davis.keygo.generated.item.data.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val passwordDao: PasswordDao
) : PasswordRepository {

    override suspend fun createNewOrUpdatePassword(password: Password): ItemId =
        passwordDao.upsert(password.toData())

    override fun observeVaultPasswords(): Flow<List<Password>> =
        passwordDao.getVaultPasswords().map { vaultPassword ->
            vaultPassword.map { it.toDomain() }
        }

    override suspend fun searchVaultPasswords(
        username: String?,
        website: String?
    ): List<Password> = passwordDao.searchVaultPassword(username, website)
        .map(VaultPassword::toDomain)

    override suspend fun getVaultPasswordById(vaultId: ItemId): Password? =
        passwordDao.getVaultPassword(vaultId)?.toDomain()

    override fun observeVaultPasswordById(vaultId: ItemId): Flow<Password> =
        passwordDao.observeVaultPassword(vaultId).map(VaultPassword::toDomain)
}