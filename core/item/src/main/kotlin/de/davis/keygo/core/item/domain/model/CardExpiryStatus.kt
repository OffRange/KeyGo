package de.davis.keygo.core.item.domain.model

import java.time.YearMonth

enum class CardExpiryStatus {
    Expired,
    ExpiresThisMonth,
    ExpiresNextMonth;

    companion object {
        fun of(expiration: YearMonth, today: YearMonth): CardExpiryStatus? = when {
            expiration < today -> Expired
            expiration == today -> ExpiresThisMonth
            expiration == today.plusMonths(1) -> ExpiresNextMonth
            else -> null
        }
    }
}
