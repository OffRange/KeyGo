package de.davis.keygo.item.presentation.password.model

sealed interface PasswordUiEvent {
    data object OnGeneratePasswordClick : PasswordUiEvent
}