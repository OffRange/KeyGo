package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CreditCardRepository] for tests.
 *
 * - Pre-populate via [seed].
 * - Force the next [createOrUpdateCreditCard] call to fail by setting [createOrUpdateError].
 * - Force [createOrUpdateCreditCard] to fail for a specific id by setting [failCreateOrUpdateForId].
 * - Flow-returning methods react to store mutations, so observers see live updates.
 */
class FakeCreditCardRepository : CreditCardRepository {

    val store = MutableStateFlow<Map<ItemId, CreditCard>>(emptyMap())

    /** Error returned by the next [createOrUpdateCreditCard] call (cleared after use). */
    var createOrUpdateError: Throwable? = null

    /**
     * If non-null, [createOrUpdateCreditCard] fails when called with this id.
     * Persists across calls; the consumer clears it explicitly.
     */
    var failCreateOrUpdateForId: Pair<ItemId, Throwable>? = null

    fun seed(vararg cards: CreditCard) {
        store.update { it + cards.associateBy { c -> c.id } }
    }

    override suspend fun createOrUpdateCreditCard(card: CreditCard): Result<ItemId, Throwable> {
        failCreateOrUpdateForId?.let { (id, error) ->
            if (id == card.id) return Result.Failure(error)
        }
        createOrUpdateError?.let {
            createOrUpdateError = null
            return Result.Failure(it)
        }
        store.update { it + (card.id to card) }
        return Result.Success(card.id)
    }

    override fun observeCreditCardById(itemId: ItemId): Flow<CreditCard?> =
        store.map { it[itemId] }

    override suspend fun getCreditCardById(itemId: ItemId): CreditCard? = store.value[itemId]

    override suspend fun getCreditCardsByVault(vaultId: VaultId): List<CreditCard> =
        store.value.values.filter { it.vaultId == vaultId }
}
