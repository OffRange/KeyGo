package de.davis.keygo.core.item.domain.model

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardExpiryStatusTest {

    private val today = YearMonth.of(2026, 9)

    @Test
    fun `a card whose month has passed is expired`() {
        assertEquals(CardExpiryStatus.Expired, CardExpiryStatus.of(YearMonth.of(2026, 8), today))
        assertEquals(CardExpiryStatus.Expired, CardExpiryStatus.of(YearMonth.of(2019, 1), today))
    }

    @Test
    fun `a card is still valid through its expiry month`() {
        assertEquals(
            CardExpiryStatus.ExpiresThisMonth,
            CardExpiryStatus.of(YearMonth.of(2026, 9), today),
        )
    }

    @Test
    fun `a card expiring the following month expires next month`() {
        assertEquals(
            CardExpiryStatus.ExpiresNextMonth,
            CardExpiryStatus.of(YearMonth.of(2026, 10), today),
        )
    }

    @Test
    fun `next month rolls over the year boundary`() {
        assertEquals(
            CardExpiryStatus.ExpiresNextMonth,
            CardExpiryStatus.of(YearMonth.of(2027, 1), YearMonth.of(2026, 12)),
        )
    }

    @Test
    fun `a card two or more months out has no status`() {
        assertNull(CardExpiryStatus.of(YearMonth.of(2026, 11), today))
        assertNull(CardExpiryStatus.of(YearMonth.of(2030, 5), today))
    }
}
