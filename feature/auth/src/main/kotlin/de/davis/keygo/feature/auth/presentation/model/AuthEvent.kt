package de.davis.keygo.feature.auth.presentation.model

internal sealed interface AuthEvent {

    data object Success : AuthEvent
    data object Failure : AuthEvent
    data object None : AuthEvent
}