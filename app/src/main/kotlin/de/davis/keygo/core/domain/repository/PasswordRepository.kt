package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.`typealias`.ItemId
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun createNewPassword(password: Password): ItemId
    fun observeVaultPasswords(): Flow<List<Password>>
    fun observeVaultPasswordById(id: ItemId): Flow<Password>
}