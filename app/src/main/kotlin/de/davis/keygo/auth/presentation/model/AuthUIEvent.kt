package de.davis.keygo.auth.presentation.model

sealed interface AuthUIEvent {

    data class PasswordChanged(val password: String) : AuthUIEvent
    data object Submit : AuthUIEvent
    data object RequestBiometricAuthentication : AuthUIEvent
}