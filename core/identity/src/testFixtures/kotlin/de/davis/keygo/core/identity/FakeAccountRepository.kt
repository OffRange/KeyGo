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

    /**
     * How many accounts were actually persisted. A caller that mints a second account overwrites
     * the first here exactly as the real registry does, so the stored value alone cannot tell the
     * two apart.
     */
    var setCount: Int = 0
        private set

    fun seed(account: Account) {
        this.account.update { account }
    }

    override suspend fun getOrNull(): Account? = account.value

    override fun observe(): Flow<Account?> = account.asStateFlow()

    override suspend fun set(account: Account): Result<Unit, Unit> {
        if (setFails) return Result.Failure(Unit)
        setCount++
        this.account.update { account }
        return Result.Success(Unit)
    }
}
