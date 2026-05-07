package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createOrUpdatePassword(login: Login): Result<ItemId, Throwable>
    suspend fun updateDomainInfos(
        itemId: ItemId,
        domainInfos: Set<DomainInfo>
    ): Result<Unit, Throwable>

    suspend fun getVaultPasswordsByTLD(
        etld1: String,
        requireTotp: Boolean = false,
        limit: Int = -1
    ): List<LiteLogin>

    suspend fun getVaultPasswordsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean = false,
        limit: Int = -1
    ): List<LiteLogin>

    suspend fun getPasswordById(itemId: ItemId): Login?

    suspend fun getPasswordsByVault(vaultId: VaultId): List<Login>

    fun observePasswordById(itemId: ItemId): Flow<Login?>
    fun observePasswords(): Flow<List<Login>>

    fun observePasswordScores(): Flow<Map<ItemId, Login.Score>>
}