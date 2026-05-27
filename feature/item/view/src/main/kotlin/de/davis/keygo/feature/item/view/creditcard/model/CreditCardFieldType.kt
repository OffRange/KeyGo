package de.davis.keygo.feature.item.view.creditcard.model

enum class CreditCardFieldType(val isSensitive: Boolean = false) {
    Holder,
    CardNumber(isSensitive = true),
    Cvv(isSensitive = true),
    Expiration,
    Tag,
    Note,
}
