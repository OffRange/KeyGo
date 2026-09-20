package de.davis.keygo.feature.password_health.presentation.model

internal sealed interface PasswordHealthUiEvent {
    data object RunHealthCheck : PasswordHealthUiEvent
    data object RefreshHealthCheck : PasswordHealthUiEvent
    
    data class OnBreachCheckChanged(val enabled: Boolean) : PasswordHealthUiEvent

    data class PasswordGenerated(val password: String) : PasswordHealthUiEvent
    data object DismissGeneratePassword : PasswordHealthUiEvent
}