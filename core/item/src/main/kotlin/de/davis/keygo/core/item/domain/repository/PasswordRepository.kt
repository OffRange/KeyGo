package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createOrUpdatePassword(password: Password): ItemId

    suspend fun getVaultPasswordsByTLD(etld1: String, limit: Int = -1): List<LitePassword>
    suspend fun getVaultPasswordsByTLDs(etld1s: Set<String>, limit: Int = -1): List<LitePassword>

    suspend fun getPasswordById(vaultId: ItemId): Password?

    fun observePasswordById(vaultId: ItemId): Flow<Password>
    fun observePasswords(): Flow<List<Password>>
}