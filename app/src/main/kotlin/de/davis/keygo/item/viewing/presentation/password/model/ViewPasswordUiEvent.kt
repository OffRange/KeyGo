package de.davis.keygo.item.viewing.presentation.password.model

import de.davis.keygo.item.core.presentation.password.model.FieldType

sealed interface ViewPasswordUiEvent {

    data object OpenWebsite : ViewPasswordUiEvent
    data object OnBackClick : ViewPasswordUiEvent

    data object OnEditRequest : ViewPasswordUiEvent

    data object OnCloseDialog : ViewPasswordUiEvent
    data object OnSubmitModification : ViewPasswordUiEvent
    data class OnModifyFieldRequest(val fieldType: FieldType) : ViewPasswordUiEvent
}