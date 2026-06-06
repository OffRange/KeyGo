package de.davis.keygo.core.identity.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.identity.data.local.model.ProtoAccountState
import de.davis.keygo.core.identity.data.mapper.toDomain
import de.davis.keygo.core.identity.data.mapper.toProto
import de.davis.keygo.core.identity.di.annotation.AccountRegistryQualifier
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class AccountRepositoryImpl(
    @param:AccountRegistryQualifier
    private val dataStore: DataStore<ProtoAccountState>,
) : AccountRepository {
    override fun observe(): Flow<Account?> = dataStore.data.map { it.toDomain() }

    override suspend fun getOrNull(): Account? = observe().firstOrNull()

    override suspend fun set(account: Account): Result<Unit, Unit> = runCatching {
        dataStore.updateData { account.toProto() }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(Unit) }
    )
}
