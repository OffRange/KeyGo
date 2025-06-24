package de.davis.keygo.viewing.presentation.model

sealed interface ViewPasswordUiEvent {

    data object OpenWebsite : ViewPasswordUiEvent
    data object OnBackClick : ViewPasswordUiEvent
}