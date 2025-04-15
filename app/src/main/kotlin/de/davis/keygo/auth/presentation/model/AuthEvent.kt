package de.davis.keygo.auth.presentation.model

sealed interface AuthEvent {

    data object Success : AuthEvent
    data object Failure : AuthEvent
    data object None : AuthEvent
}