package de.davis.keygo.core.identity

import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.util.Result

class FakeAccountRepository : AccountRepository {

    private var account: Account? = null

    var setFails: Boolean = false

    fun seed(account: Account) {
        this.account = account
    }

    override suspend fun getOrNull(): Account? = account

    override suspend fun set(account: Account): Result<Unit, Unit> {
        if (setFails) return Result.Failure(Unit)
        this.account = account
        return Result.Success(Unit)
    }
}
