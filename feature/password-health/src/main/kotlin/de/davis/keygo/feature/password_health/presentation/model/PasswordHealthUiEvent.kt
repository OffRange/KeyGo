package de.davis.keygo.feature.password_health.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId

internal sealed interface PasswordHealthUiEvent {
    data object RunHealthCheck : PasswordHealthUiEvent
    data object RefreshHealthCheck : PasswordHealthUiEvent

    data class ItemClicked(val itemId: ItemId) : PasswordHealthUiEvent

    data class OnBreachCheckChanged(val enabled: Boolean) : PasswordHealthUiEvent
    data class OnNotificationChanged(val enabled: Boolean) : PasswordHealthUiEvent

    data class FixClicked(val itemId: ItemId) : PasswordHealthUiEvent
    data class PasswordGenerated(val password: String) : PasswordHealthUiEvent
    data object DismissGeneratePassword : PasswordHealthUiEvent

    data class OpenSite(val url: String) : PasswordHealthUiEvent

    data object ConfirmPasswordChanged : PasswordHealthUiEvent

    data object DiscardFix : PasswordHealthUiEvent
}
