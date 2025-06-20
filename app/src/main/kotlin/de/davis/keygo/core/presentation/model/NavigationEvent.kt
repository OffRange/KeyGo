package de.davis.keygo.core.presentation.model

sealed interface NavigationEvent {

    data object NavigateBack : NavigationEvent
}