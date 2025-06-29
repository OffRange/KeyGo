package de.davis.keygo.item.create.presentation.password.model

sealed interface GeneratePasswordUiEvent : PasswordUiEvent {
    data class OnCharacterSetClick(val uiCharacterSet: UiCharacterSet) : GeneratePasswordUiEvent

    data object OnGeneratePasswordClick : GeneratePasswordUiEvent
    data object OnUseClick : GeneratePasswordUiEvent
}