package de.davis.keygo.feature.item.create.presentation.creditcard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardBaseState
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditCardBaseStateTest {

    private val formatter = DateTimeFormatter.ofPattern("MM/yy")

    private fun stateWithExpiration(text: String) = CreditCardBaseState(
        ccExpirationDateTextFieldState = TextFieldState(text),
    )

    @Test
    fun `a past expiration reads as expired`() {
        val text = YearMonth.now().minusMonths(1).format(formatter)

        assertEquals(CardExpiryStatus.Expired, stateWithExpiration(text).expiryStatus)
    }

    @Test
    fun `an incomplete or empty expiration has no status`() {
        assertNull(stateWithExpiration("").expiryStatus)
        assertNull(stateWithExpiration("0").expiryStatus)
        assertNull(stateWithExpiration("13/30").expiryStatus)
    }

    @Test
    fun `typing a new date replaces the status`() {
        val state = stateWithExpiration(YearMonth.now().format(formatter))
        assertEquals(CardExpiryStatus.ExpiresThisMonth, state.expiryStatus)

        state.ccExpirationDateTextFieldState.setTextAndPlaceCursorAtEnd(
            YearMonth.now().plusYears(3).format(formatter),
        )

        assertNull(state.expiryStatus)
    }
}
