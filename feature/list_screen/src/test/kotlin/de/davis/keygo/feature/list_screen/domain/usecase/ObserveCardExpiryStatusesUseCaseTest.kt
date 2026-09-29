package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveCardExpiryStatusesUseCaseTest {

    private val creditCardRepository = FakeCreditCardRepository()
    private val useCase = ObserveCardExpiryStatusesUseCase(creditCardRepository)

    private fun card(expirationDate: YearMonth?) = CreditCard(
        id = newItemId(),
        vaultId = newVaultId(),
        name = "Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        timestamp = Timestamp(),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = null,
        cardNumber = null,
        cvv = null,
        expirationDate = expirationDate,
    )

    @Test
    fun `only cards that are expired or about to expire get a status`() = runTest {
        val today = YearMonth.now()
        val expired = card(today.minusMonths(1))
        val expiresThisMonth = card(today)
        val expiresNextMonth = card(today.plusMonths(1))
        val farOut = card(today.plusYears(3))
        val undated = card(null)
        creditCardRepository.seed(expired, expiresThisMonth, expiresNextMonth, farOut, undated)

        assertEquals(
            mapOf(
                expired.id to CardExpiryStatus.Expired,
                expiresThisMonth.id to CardExpiryStatus.ExpiresThisMonth,
                expiresNextMonth.id to CardExpiryStatus.ExpiresNextMonth,
            ),
            useCase().first(),
        )
    }

    @Test
    fun `renewing a card clears its status`() = runTest {
        val today = YearMonth.now()
        val renewed = card(today.minusMonths(2))
        creditCardRepository.seed(renewed)

        creditCardRepository.seed(renewed.copy(expirationDate = today.plusYears(4)))

        assertEquals(emptyMap(), useCase().first())
    }
}
