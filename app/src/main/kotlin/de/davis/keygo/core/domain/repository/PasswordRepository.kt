package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.Password
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createNewOrUpdatePassword(password: Password): ItemId
    fun observeVaultPasswords(): Flow<List<Password>>

    /**
     * Finds all [Password] pairs where the username or website
     * contains the given **substrings** (case-insensitive).
     */
    suspend fun searchVaultPasswords(
        username: String? = null,
        website: String? = null
    ): List<Password>

    /**
     * Retrieves all [Password]s where the stored website
     * appears **anywhere inside** the provided URL (case-insensitive).
     */
    suspend fun findVaultPasswordsByUrl(url: String): List<Password>

    suspend fun getVaultPasswordById(vaultId: ItemId): Password?
    fun observeVaultPasswordById(vaultId: ItemId): Flow<Password>
}