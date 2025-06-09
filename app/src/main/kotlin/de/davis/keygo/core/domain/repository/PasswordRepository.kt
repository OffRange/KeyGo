package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.Password
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createNewPassword(password: Password): Long
    fun observeVaultPasswords(): Flow<List<Password>>
    fun observeVaultPasswordById(id: Long): Flow<Password>
}