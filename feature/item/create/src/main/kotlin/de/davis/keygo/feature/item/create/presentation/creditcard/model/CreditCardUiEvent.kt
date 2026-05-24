package de.davis.keygo.feature.item.create.presentation.creditcard.model

import de.davis.keygo.feature.item.create.presentation.model.ItemUiEvent

internal sealed interface CreditCardUiEvent {
    data class ItemUi(val event: ItemUiEvent) : CreditCardUiEvent
}
