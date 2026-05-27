package de.davis.keygo.feature.item.view.creditcard.model

sealed interface ViewCreditCardUiEvent {

    data object OnBackClick : ViewCreditCardUiEvent
    data object OnPinClick : ViewCreditCardUiEvent
    data object OnEditRequest : ViewCreditCardUiEvent

    data object OnCloseDialog : ViewCreditCardUiEvent
    data class OnSubmitModification(val input: String) : ViewCreditCardUiEvent
    data class OnModifyFieldRequest(val fieldType: CreditCardFieldType) : ViewCreditCardUiEvent
}
