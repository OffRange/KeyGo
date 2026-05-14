package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

interface LoginRepository {

    suspend fun createOrUpdateLogin(login: Login): Result<ItemId, Throwable>

    suspend fun updateDomainInfos(
        itemId: ItemId,
        domainInfos: Set<DomainInfo>,
    ): Result<Unit, Throwable>

    suspend fun getLoginsByTLD(
        etld1: String,
        requireTotp: Boolean = false,
        requirePassword: Boolean = false,
        limit: Int = -1,
    ): List<LiteLogin>

    suspend fun getLoginsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean = false,
        requirePassword: Boolean = false,
        limit: Int = -1,
    ): List<LiteLogin>

    suspend fun getLoginById(itemId: ItemId): Login?

    suspend fun getLoginsByVault(vaultId: VaultId): List<Login>

    fun observeLoginById(itemId: ItemId): Flow<Login?>
    fun observeLogins(): Flow<List<Login>>

    fun observePasswordScores(): Flow<Map<ItemId, PasswordScore>>
}
