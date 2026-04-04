package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItem
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.data.maper.toDataDomainInfos
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val database: ItemDatabase,
    private val vaultDao: VaultDao,
    private val passwordDao: PasswordDao,
    private val domainInfoDao: DomainInfoDao
) : PasswordRepository {

    override suspend fun createOrUpdatePassword(password: Password): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                val vaultItemId = vaultDao.upsert((password as VaultItem).toData())
                    .takeIf { it != -1L }
                    ?: password.vaultItemId // room returned -1 meaning the item was updated

                val passwordId =
                    passwordDao.upsert(password.toData(vaultItemId))
                        .takeIf { it != -1L }
                        ?: password.id

                domainInfoDao.syncForPassword(passwordId, password.toDataDomainInfos(passwordId))

                passwordId
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) }
        )

    override suspend fun updatePasswordWithDomainInfo(
        vaultItemId: ItemId,
        domainInfos: Set<DomainInfo>
    ): Result<Unit, Throwable> = runCatching {
        database.withTransaction {
            val password = passwordDao.getVaultPassword(vaultItemId)
                ?: throw IllegalArgumentException("No password found with vault id $vaultItemId")

            val dataDomains = domainInfos.map { it.toData(password.passwordEntity.id) }.toSet()
            domainInfoDao.upsertAll(dataDomains)
        }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(it) }
    )

    override suspend fun getVaultPasswordsByTLD(
        etld1: String,
        requireTotp: Boolean,
        limit: Int
    ): List<LitePassword> =
        getVaultPasswordsByTLDs(setOf(etld1), requireTotp, limit)

    override suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int
    ): List<LitePassword> =
        passwordDao.getByTLDs(etld1s, requireTotp, limit).map(LightweightPassword::toDomain)

    override suspend fun getPasswordById(vaultId: ItemId): Password? =
        passwordDao.getVaultPassword(vaultId)?.toDomain()

    override fun observePasswordById(vaultId: ItemId): Flow<Password?> =
        passwordDao.observeVaultPassword(vaultId)
            .map { it?.toDomain() }

    override fun observePasswords(): Flow<List<Password>> = passwordDao.getAllPasswords().map {
        it.map(VaultPassword::toDomain)
    }

    override fun observeLitePasswords(): Flow<List<LiteItem>> =
        passwordDao.getLitePasswords().map {
            it.map(LightweightVaultItem::toDomain)
        }

    override suspend fun searchPasswordItem(query: String): List<LiteVaultItemSearchResult> =
        passwordDao.searchPasswordItem(query).map { it.toDomain() }

    override fun observePasswordScores(): Flow<Map<ItemId, Password.Score>> =
        passwordDao.observePasswordScores().map { entries ->
            entries.associate { it.vaultItemId to it.score }
        }

    override suspend fun getPasswordIdByVaultId(vaultId: ItemId): ItemId? =
        passwordDao.getPasswordIdByVaultId(vaultId)
}