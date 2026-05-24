package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow

interface CreditCardRepository {
    suspend fun createOrUpdateCreditCard(card: CreditCard): Result<ItemId, Throwable>

    fun observeCreditCardById(itemId: ItemId): Flow<CreditCard?>
    suspend fun getCreditCardById(itemId: ItemId): CreditCard?
}
