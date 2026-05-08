package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.LoginDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.data.maper.toDomainInfoEntities
import de.davis.keygo.core.item.data.maper.toLoginEntity
import de.davis.keygo.core.item.data.maper.toPasswordEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class LoginRepositoryImpl(
    private val database: ItemDatabase,
    private val itemDao: ItemDao,
    private val loginDao: LoginDao,
    private val passwordDao: PasswordDao,
    private val domainInfoDao: DomainInfoDao,
    private val totpDao: TotpDao,
) : LoginRepository {

    override suspend fun createOrUpdateLogin(login: Login): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                itemDao.upsert((login as Item).toData())
                loginDao.upsert(login.toLoginEntity())
                passwordDao.upsert(login.toPasswordEntity())
                login.totp?.toData()?.let {
                    totpDao.upsert(it)
                }
                domainInfoDao.syncForLogin(login.id, login.toDomainInfoEntities(login.id))

                login.id
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) },
        )

    override suspend fun updateDomainInfos(
        itemId: ItemId,
        domainInfos: Set<DomainInfo>,
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

    override suspend fun getLoginsByTLD(
        etld1: String,
        requireTotp: Boolean,
        limit: Int,
    ): List<LiteLogin> = getLoginsByTLDs(setOf(etld1), requireTotp, limit)

    override suspend fun getLoginsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int,
    ): List<LiteLogin> =
        loginDao.getByTLDs(etld1s, requireTotp, limit).map(LightweightLogin::toDomain)

    override suspend fun getLoginById(itemId: ItemId): Login? =
        loginDao.getById(itemId)?.toDomain()

    override suspend fun getLoginsByVault(vaultId: VaultId): List<Login> =
        loginDao.getByVault(vaultId).map(LoginProjection::toDomain)

    override fun observeLoginById(itemId: ItemId): Flow<Login?> =
        loginDao.observeById(itemId).map { it?.toDomain() }

    override fun observeLogins(): Flow<List<Login>> =
        loginDao.observeAll().map { it.map(LoginProjection::toDomain) }

    override fun observePasswordScores(): Flow<Map<ItemId, PasswordScore>> =
        passwordDao.observeScores().map { entries ->
            entries.associate { it.id to it.passwordScore }
        }
}
