package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.LoginDao
import de.davis.keygo.core.item.data.local.dao.PasskeyDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TagDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.data.mapper.toData
import de.davis.keygo.core.item.data.mapper.toDomain
import de.davis.keygo.core.item.data.mapper.toDomainInfoEntities
import de.davis.keygo.core.item.data.mapper.toLoginEntity
import de.davis.keygo.core.item.data.mapper.toPasswordEntity
import de.davis.keygo.core.item.data.mapper.toTagEntities
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.TransactionRunner
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class LoginRepositoryImpl(
    private val transactionRunner: TransactionRunner,
    private val itemDao: ItemDao,
    private val loginDao: LoginDao,
    private val passwordDao: PasswordDao,
    private val domainInfoDao: DomainInfoDao,
    private val totpDao: TotpDao,
    private val tagDao: TagDao,
    private val passkeyDao: PasskeyDao,
) : LoginRepository {

    override suspend fun createOrUpdateLogin(login: Login): Result<ItemId, Throwable> =
        runCatching {
            transactionRunner.runInTransaction {
                itemDao.upsert((login as Item).toData())
                loginDao.upsert(login.toLoginEntity())

                login.toPasswordEntity()?.let { passwordDao.upsert(it) }
                    ?: passwordDao.delete(login.id)

                login.totp?.toData()?.let {
                    totpDao.upsert(it)
                } ?: totpDao.delete(login.id)

                domainInfoDao.syncForLogin(login.id, login.toDomainInfoEntities())
                tagDao.syncTags(login.id, login.tags.toTagEntities())

                // Delete only: passkeys are created through PasskeyRepository, never from here.
                // See Login.passkeys for why this set has to come from a fresh read.
                passkeyDao.deleteCredentialsNotIn(login.id, login.passkeys.map { it.credentialId })

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
            transactionRunner.runInTransaction {
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
        requirePassword: Boolean,
        requireUsername: Boolean,
        limit: Int,
    ): List<LiteLogin> =
        getLoginsByTLDs(setOf(etld1), requireTotp, requirePassword, requireUsername, limit)

    override suspend fun getLoginsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        requirePassword: Boolean,
        requireUsername: Boolean,
        limit: Int,
    ): List<LiteLogin> =
        loginDao.getByTLDs(etld1s, requireTotp, requirePassword, requireUsername, limit)
            .map(LightweightLogin::toDomain)

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
