package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val database: ItemDatabase,
    private val vaultDao: VaultDao,
    private val passwordDao: PasswordDao
) : PasswordRepository {

    override suspend fun createOrUpdatePassword(password: Password): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                val vaultItemId = vaultDao.upsert((password as VaultItem).toData())
                    .takeIf { it != -1L }
                    ?: password.vaultItemId // room returned -1 meaning the item was updated
                
                passwordDao.upsert(password.toData().copy(vaultItemId = vaultItemId))
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) }
        )

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