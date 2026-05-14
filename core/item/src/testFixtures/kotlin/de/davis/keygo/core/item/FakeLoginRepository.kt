package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [LoginRepository] for tests.
 *
 * - Pre-populate via [seed].
 * - Force the next [createOrUpdateLogin] call to fail by setting [createOrUpdateError].
 * - Force [createOrUpdateLogin] to fail for a specific id by setting [failCreateOrUpdateForId].
 * - Flow-returning methods react to store mutations, so observers see live updates.
 */
class FakeLoginRepository : LoginRepository {

    internal val store = MutableStateFlow<Map<ItemId, Login>>(emptyMap())

    /** Error returned by the next [createOrUpdateLogin] call (cleared after use). */
    var createOrUpdateError: Throwable? = null

    /**
     * If non-null, [createOrUpdateLogin] fails when called with this id.
     * Persists across calls; the consumer clears it explicitly.
     */
    var failCreateOrUpdateForId: Pair<ItemId, Throwable>? = null

    fun seed(vararg logins: Login) {
        store.update { it + logins.associateBy { p -> p.id } }
    }

    override suspend fun createOrUpdateLogin(login: Login): Result<ItemId, Throwable> {
        failCreateOrUpdateForId?.let { (id, error) ->
            if (id == login.id) return Result.Failure(error)
        }
        createOrUpdateError?.let {
            createOrUpdateError = null
            return Result.Failure(it)
        }
        store.update { it + (login.id to login) }
        return Result.Success(login.id)
    }

    override suspend fun updateDomainInfos(
        itemId: ItemId,
        domainInfos: Set<DomainInfo>,
    ): Result<Unit, Throwable> {
        val existing = store.value[itemId]
            ?: return Result.Failure(NoSuchElementException("No login with id $itemId"))
        store.update { it + (itemId to existing.copy(domainInfos = domainInfos)) }
        return Result.Success(Unit)
    }

    override suspend fun getLoginsByTLD(
        etld1: String,
        requireTotp: Boolean,
        requirePassword: Boolean,
        limit: Int,
    ): List<LiteLogin> = emptyList()

    override suspend fun getLoginsByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        requirePassword: Boolean,
        limit: Int,
    ): List<LiteLogin> = emptyList()

    override suspend fun getLoginById(itemId: ItemId): Login? = store.value[itemId]

    override suspend fun getLoginsByVault(vaultId: VaultId): List<Login> =
        store.value.values.filter { it.vaultId == vaultId }

    override fun observeLoginById(itemId: ItemId): Flow<Login?> =
        store.map { it[itemId] }

    override fun observeLogins(): Flow<List<Login>> =
        store.map { it.values.toList() }

    override fun observePasswordScores(): Flow<Map<ItemId, PasswordScore>> =
        store.map { logins ->
            logins.values
                .mapNotNull { login -> login.passwordCredential?.let { login.id to it.score } }
                .toMap()
        }
}
