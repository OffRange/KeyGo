package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val passwordDao: PasswordDao
) : PasswordRepository {

    override suspend fun createOrUpdatePassword(password: Password): ItemId =
        passwordDao.upsert(password.toData())

    override suspend fun getVaultPasswordsByTLD(etld1: String, limit: Int): List<LitePassword> =
        getVaultPasswordsByTLDs(setOf(etld1), limit)

    override suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        limit: Int
    ): List<LitePassword> =
        passwordDao.getByTLDs(etld1s, limit).map(LightweightPassword::toDomain)

    override suspend fun getPasswordById(vaultId: ItemId): Password? =
        passwordDao.getVaultPassword(vaultId)?.toDomain()

    override fun observePasswordById(vaultId: ItemId): Flow<Password> =
        passwordDao.observeVaultPassword(vaultId)
            .map(VaultPassword::toDomain)

    override fun observePasswords(): Flow<List<Password>> = passwordDao.getAllPasswords().map {
        it.map(VaultPassword::toDomain)
    }
}