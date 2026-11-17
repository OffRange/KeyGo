package de.davis.keygo.feature.settings.presentation.account.models

internal sealed interface AccountUiEvent {
    data object OnBackClicked : AccountUiEvent
    data object OnScanInviteClicked : AccountUiEvent
    data object OnConnectClicked : AccountUiEvent

    data object ResetPassword : AccountUiEvent
}
