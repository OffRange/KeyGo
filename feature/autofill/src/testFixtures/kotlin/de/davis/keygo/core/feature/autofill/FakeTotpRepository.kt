package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.TotpRepository

/**
 * In-memory [TotpRepository] for tests.
 * Seed via [seed]. Returns null for unseeded ids.
 */
class FakeTotpRepository : TotpRepository {
    private val store = mutableMapOf<ItemId, Totp>()
    fun seed(id: ItemId, totp: Totp) {
        store[id] = totp
    }

    override suspend fun getTotp(loginId: ItemId): Totp? = store[loginId]
}
