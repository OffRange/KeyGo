package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import java.time.YearMonth

@Single
class ObserveCardExpiryStatusesUseCase(
    private val creditCardRepository: CreditCardRepository,
) {

    operator fun invoke(): Flow<Map<ItemId, CardExpiryStatus>> =
        creditCardRepository.observeExpirationDates().map { expirations ->
            val today = YearMonth.now()
            buildMap {
                expirations.forEach { (id, expiration) ->
                    CardExpiryStatus.of(expiration, today)?.let { put(id, it) }
                }
            }
        }
}
