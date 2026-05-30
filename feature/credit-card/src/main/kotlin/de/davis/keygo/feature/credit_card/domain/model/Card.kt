package de.davis.keygo.feature.credit_card.domain.model

import java.time.YearMonth

data class Card(
    val holder: String,
    val cardNumber: String,
    val expiry: YearMonth,
)
