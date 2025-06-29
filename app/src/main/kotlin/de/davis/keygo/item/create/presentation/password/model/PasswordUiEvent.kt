package de.davis.keygo.item.create.presentation.password.model

sealed interface PasswordUiEvent {
    data object OnSubmit : PasswordUiEvent
    data object OnGeneratePasswordClick : PasswordUiEvent
    data object OnBackClick : PasswordUiEvent
    data object OnCloseBottomSheet : PasswordUiEvent
}