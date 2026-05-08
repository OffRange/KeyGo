package de.davis.keygo.feature.item.view.login.model

import de.davis.keygo.feature.item.core.presentation.login.model.FieldType

sealed interface ViewLoginUiEvent {

    data class OpenWebsite(val domain: String) : ViewLoginUiEvent
    data object OnBackClick : ViewLoginUiEvent

    data object OnPinClick : ViewLoginUiEvent
    data object OnEditRequest : ViewLoginUiEvent

    data object OnCloseDialog : ViewLoginUiEvent
    data class OnSubmitModification(val input: String) : ViewLoginUiEvent
    data class OnModifyFieldRequest(val fieldType: FieldType) : ViewLoginUiEvent

    data object OnScanCodeRequest : ViewLoginUiEvent
    data class OnCodesScanned(val codes: List<String>) : ViewLoginUiEvent
}
