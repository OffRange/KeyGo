package de.davis.keygo.feature.item.core.domain.model

/**
 * Credit-card-specific upsert errors
 */
sealed interface CreditCardUpsertError : ItemUpsertError {
    data object InvalidExpiration : CreditCardUpsertError
    data object InvalidCardNumber : CreditCardUpsertError
}
