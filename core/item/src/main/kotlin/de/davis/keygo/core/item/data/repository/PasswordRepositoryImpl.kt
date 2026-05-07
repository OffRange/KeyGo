package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDataDomainInfos
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
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

    override suspend fun createOrUpdatePassword(login: Login): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                itemDao.upsert((login as Item).toData())
                passwordDao.upsert(login.toData())
                login.totp?.toData()?.let {
                    totpDao.upsert(it)
                }
                domainInfoDao.syncForPassword(login.id, login.toDataDomainInfos(login.id))

                login.id
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
    ): List<LiteLogin> = getVaultPasswordsByTLDs(setOf(etld1), requireTotp, limit)

    override suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int,
    ): List<LiteLogin> =
        passwordDao.getByTLDs(etld1s, requireTotp, limit).map(LightweightLogin::toDomain)

    override suspend fun getPasswordById(itemId: ItemId): Login? =
        passwordDao.getVaultPassword(itemId)?.toDomain()

    override suspend fun getPasswordsByVault(vaultId: VaultId): List<Login> =
        passwordDao.getPasswordsByVault(vaultId).map(LoginProjection::toDomain)

    override fun observePasswordById(itemId: ItemId): Flow<Login?> =
        passwordDao.observeVaultPassword(itemId).map { it?.toDomain() }

    override fun observePasswords(): Flow<List<Login>> =
        passwordDao.getAllPasswords().map { it.map(LoginProjection::toDomain) }

    override fun observePasswordScores(): Flow<Map<ItemId, Login.Score>> =
        passwordDao.observePasswordScores().map { entries ->
            entries.associate { it.id to it.score }
        }
}
