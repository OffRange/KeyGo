package de.davis.keygo.core.identity

import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeAccountRepository : AccountRepository {

    private val account = MutableStateFlow<Account?>(null)

    var setFails: Boolean = false

    /**
     * When set, [getOrNull] suspends on it and answers with whatever it is completed to.
     *
     * The only place a test can stand inside an unlock. Reading the account is the first thing
     * `UnlockWithPasswordUseCase` does and everything after it hops to `Dispatchers.Default`, which
     * the test scheduler cannot see, so this is the one suspension a test can both hold open and
     * release on demand.
     */
    var pendingRead: CompletableDeferred<Account?>? = null

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

    // Not an elvis over await(): completing the read with null is the point of it, and an elvis
    // would quietly hand back the seeded account instead.
    override suspend fun getOrNull(): Account? = when (val pending = pendingRead) {
        null -> account.value
        else -> pending.await()
    }

    override fun observe(): Flow<Account?> = account.asStateFlow()

    override suspend fun set(account: Account): Result<Unit, Unit> {
        if (setFails) return Result.Failure(Unit)
        setCount++
        this.account.update { account }
        return Result.Success(Unit)
    }
}
