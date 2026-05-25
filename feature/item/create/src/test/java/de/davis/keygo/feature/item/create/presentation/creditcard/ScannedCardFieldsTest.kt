package de.davis.keygo.feature.item.create.presentation.creditcard

import de.davis.keygo.feature.credit_card.domain.model.Card
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScannedCardFieldsTest {

    @Test
    fun mapsNumberHolderAndFormatsExpiry() {
        val fields = Card(
            holder = "JANE DOE",
            cardNumber = "4111111111111111",
            expiry = YearMonth.of(2030, 12),
        ).toScannedFields()

        assertEquals("4111111111111111", fields.number)
        assertEquals("12/30", fields.expiry)
        assertEquals("JANE DOE", fields.holder)
    }

    @Test
    fun blankHolderBecomesNull() {
        val fields = Card(
            holder = "   ",
            cardNumber = "5555555555554444",
            expiry = YearMonth.of(2026, 1),
        ).toScannedFields()

        assertNull(fields.holder)
        assertEquals("01/26", fields.expiry)
    }
}
