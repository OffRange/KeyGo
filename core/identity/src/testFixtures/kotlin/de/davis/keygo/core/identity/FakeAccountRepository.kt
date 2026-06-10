package de.davis.keygo.core.identity

import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeAccountRepository : AccountRepository {

    private val account = MutableStateFlow<Account?>(null)

    var setFails: Boolean = false

    fun seed(account: Account) {
        this.account.update { account }
    }

    override suspend fun getOrNull(): Account? = account.value

    override fun observe(): Flow<Account?> = account.asStateFlow()

    override suspend fun set(account: Account): Result<Unit, Unit> {
        if (setFails) return Result.Failure(Unit)
        this.account.update { account }
        return Result.Success(Unit)
    }
}
