package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDataDomainInfos
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val database: ItemDatabase,
    private val itemDao: ItemDao,
    private val passwordDao: PasswordDao,
    private val domainInfoDao: DomainInfoDao,
    private val totpDao: TotpDao,
) : PasswordRepository {

    override suspend fun createOrUpdatePassword(password: Password): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                itemDao.upsert((password as Item).toData())
                passwordDao.upsert(password.toData())
                password.totp?.toData()?.let {
                    totpDao.upsert(it)
                }
                domainInfoDao.syncForPassword(password.id, password.toDataDomainInfos(password.id))

                password.id
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) },
        )

    override suspend fun updateDomainInfos(
        itemId: ItemId,
        domainInfos: Set<DomainInfo>
    ): Result<Unit, Throwable> =
        runCatching {
            database.withTransaction {
                val dataDomains = domainInfos.map { it.toData(itemId) }.toSet()
                domainInfoDao.upsertAll(dataDomains)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Failure(it) },
        )

    override suspend fun getVaultPasswordsByTLD(
        etld1: String,
        requireTotp: Boolean,
        limit: Int,
    ): List<LitePassword> = getVaultPasswordsByTLDs(setOf(etld1), requireTotp, limit)

    override suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int,
    ): List<LitePassword> =
        passwordDao.getByTLDs(etld1s, requireTotp, limit).map(LightweightPassword::toDomain)

    override suspend fun getPasswordById(itemId: ItemId): Password? =
        passwordDao.getVaultPassword(itemId)?.toDomain()

    override suspend fun getPasswordsByVault(vaultId: VaultId): List<Password> =
        passwordDao.getPasswordsByVault(vaultId).map(VaultPassword::toDomain)

    override fun observePasswordById(itemId: ItemId): Flow<Password?> =
        passwordDao.observeVaultPassword(itemId).map { it?.toDomain() }

    override fun observePasswords(): Flow<List<Password>> =
        passwordDao.getAllPasswords().map { it.map(VaultPassword::toDomain) }

    override fun observePasswordScores(): Flow<Map<ItemId, Password.Score>> =
        passwordDao.observePasswordScores().map { entries ->
            entries.associate { it.id to it.score }
        }
}
