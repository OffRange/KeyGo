package de.davis.keygo.feature.item.create.presentation.creditcard

import de.davis.keygo.feature.credit_card.domain.model.Card
import java.time.format.DateTimeFormatter

internal data class ScannedCardFields(
    val number: String,
    val expiry: String,
    val holder: String?,
)

internal val CC_EXPIRATION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")

internal fun Card.toScannedFields(): ScannedCardFields = ScannedCardFields(
    number = cardNumber,
    expiry = expiry.format(CC_EXPIRATION_FORMATTER),
    holder = holder.takeIf { it.isNotBlank() },
)
