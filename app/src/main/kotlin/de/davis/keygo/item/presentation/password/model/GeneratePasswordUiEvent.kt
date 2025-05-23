package de.davis.keygo.item.presentation.password.model

sealed interface GeneratePasswordUiEvent : PasswordUiEvent {
    data class OnCharacterSetClick(val uiCharacterSet: UiCharacterSet) : GeneratePasswordUiEvent

    data object OnGeneratePasswordClick : GeneratePasswordUiEvent
    data object OnUseClick : GeneratePasswordUiEvent
}