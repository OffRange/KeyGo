package de.davis.keygo.feature.auth.presentation.model

sealed interface AuthUIEvent {

    data object Submit : AuthUIEvent
    data object RequestBiometricAuthentication : AuthUIEvent
}