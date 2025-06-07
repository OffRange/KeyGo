package de.davis.keygo.core.data.repository

import de.davis.keygo.core.data.local.dao.PasswordDao
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.generated.item.data.mapper.toData
import de.davis.keygo.generated.item.data.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class PasswordRepositoryImpl(
    private val passwordDao: PasswordDao
) : PasswordRepository {

    override suspend fun createNewPassword(password: Password): Long =
        passwordDao.insert(password.toData())

    override fun observeVaultPasswords(): Flow<List<Password>> =
        passwordDao.getVaultPasswords().map { vaultPassword ->
            vaultPassword.map { it.toDomain() }
        }
}