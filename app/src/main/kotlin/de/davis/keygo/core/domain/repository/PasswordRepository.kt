package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.Password
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createNewOrUpdatePassword(password: Password): ItemId
    fun observeVaultPasswords(): Flow<List<Password>>

    suspend fun getVaultPasswordById(vaultId: ItemId): Password?
    fun observeVaultPasswordById(vaultId: ItemId): Flow<Password>
}