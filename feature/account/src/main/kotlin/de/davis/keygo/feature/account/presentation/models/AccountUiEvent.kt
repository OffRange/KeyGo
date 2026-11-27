package de.davis.keygo.feature.account.presentation.models

internal sealed interface AccountUiEvent {
    data object OnBackClicked : AccountUiEvent
    data object OnScanInviteClicked : AccountUiEvent
    data object OnConnectClicked : AccountUiEvent
    data object OnDevicesClicked : AccountUiEvent
    data object OnLogoutClicked : AccountUiEvent
    data object OnLogoutConfirmed : AccountUiEvent
    data object OnLogoutDismissed : AccountUiEvent

    data object OnDeleteAccountClicked : AccountUiEvent
    data object OnDeleteAccountConfirmed : AccountUiEvent
    data object OnDeleteAccountDismissed : AccountUiEvent

    data object ResetPassword : AccountUiEvent
}
