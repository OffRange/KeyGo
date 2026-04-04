package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createOrUpdatePassword(password: Password): Result<ItemId, Throwable>
    suspend fun updatePasswordWithDomainInfo(
        vaultItemId: ItemId,
        domainInfos: Set<DomainInfo>
    ): Result<Unit, Throwable>

    suspend fun getVaultPasswordsByTLD(
        etld1: String,
        requireTotp: Boolean = false,
        limit: Int = -1
    ): List<LitePassword>

    suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean = false,
        limit: Int = -1
    ): List<LitePassword>

    suspend fun getPasswordById(vaultId: ItemId): Password?

    fun observePasswordById(vaultId: ItemId): Flow<Password?>
    fun observePasswords(): Flow<List<Password>>

    fun observePasswordScores(): Flow<Map<ItemId, Password.Score>>

    suspend fun getPasswordIdByVaultId(vaultId: ItemId): ItemId?
}