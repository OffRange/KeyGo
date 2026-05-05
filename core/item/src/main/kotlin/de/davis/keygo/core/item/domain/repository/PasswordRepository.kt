package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createOrUpdatePassword(password: Password): Result<ItemId, Throwable>
    suspend fun updateDomainInfos(
        itemId: ItemId,
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

    suspend fun getPasswordById(itemId: ItemId): Password?

    suspend fun getPasswordsByVault(vaultId: VaultId): List<Password>

    fun observePasswordById(itemId: ItemId): Flow<Password?>
    fun observePasswords(): Flow<List<Password>>

    fun observePasswordScores(): Flow<Map<ItemId, Password.Score>>
}