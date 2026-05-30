package de.davis.keygo.feature.credit_card.data.mapper

import com.github.devnied.emvnfccard.model.EmvCard
import de.davis.keygo.feature.credit_card.domain.model.Card
import java.time.YearMonth
import java.time.ZoneId

internal fun EmvCard.toDomain(): Card? {
    val digits = cardNumber?.filter(Char::isDigit).orEmpty()
    if (digits.isBlank()) return null

    return Card(
        holder = listOfNotNull(holderFirstname, holderLastname).joinToString(" ").trim(),
        cardNumber = cardNumber,
        expiry = expireDate.toInstant().atZone(ZoneId.systemDefault()).let { YearMonth.from(it) }
    )
}