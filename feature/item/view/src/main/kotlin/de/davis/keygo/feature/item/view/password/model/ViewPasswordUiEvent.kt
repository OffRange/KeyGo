package de.davis.keygo.feature.item.view.password.model

import de.davis.keygo.feature.item.core.presentation.password.model.FieldType

sealed interface ViewPasswordUiEvent {

    data class OpenWebsite(val domain: String) : ViewPasswordUiEvent
    data object OnBackClick : ViewPasswordUiEvent

    data object OnEditRequest : ViewPasswordUiEvent

    data object OnCloseDialog : ViewPasswordUiEvent
    data class OnSubmitModification(val input: String) : ViewPasswordUiEvent
    data class OnModifyFieldRequest(val fieldType: FieldType) : ViewPasswordUiEvent

    data object OnScanCodeRequest : ViewPasswordUiEvent
    data class OnCodesScanned(val codes: List<String>) : ViewPasswordUiEvent
}