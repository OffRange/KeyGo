package de.davis.keygo.auth.presentation.model

sealed interface AuthUIEvent {

    data object Submit : AuthUIEvent
    data object RequestBiometricAuthentication : AuthUIEvent

    data object CloseMigrationDialog : AuthUIEvent

    data class ToggleUseBiometrics(val checked: Boolean) : AuthUIEvent
}