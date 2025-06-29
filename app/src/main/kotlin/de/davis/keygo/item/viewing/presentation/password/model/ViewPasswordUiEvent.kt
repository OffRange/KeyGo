package de.davis.keygo.item.viewing.presentation.password.model

sealed interface ViewPasswordUiEvent {

    data object OpenWebsite : ViewPasswordUiEvent
    data object OnBackClick : ViewPasswordUiEvent

    data object OnEditRequest : ViewPasswordUiEvent

    data object OnCloseDialog : ViewPasswordUiEvent
    data object OnSubmitModification : ViewPasswordUiEvent
    data class OnModifyFieldRequest(val fieldType: FieldType) : ViewPasswordUiEvent
}