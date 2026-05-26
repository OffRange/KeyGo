package de.davis.keygo.feature.item.core.domain.model

/**
 * Credit-card-specific upsert errors, surfaced through the shared [ItemUpsertError] channel.
 *
 * Card-number format validation (length, Luhn) is intentionally not modelled here: the entry form
 * gates a non-blank number, and a blank number is caught generically as [ItemUpsertError.Empty].
 */
sealed interface CreditCardUpsertError : ItemUpsertError {
    /** The expiration field could not be parsed as an `MM/yy` year-month. */
    data object InvalidExpiration : CreditCardUpsertError
}
