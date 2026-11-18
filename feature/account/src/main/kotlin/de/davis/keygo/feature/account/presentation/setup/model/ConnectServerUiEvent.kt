package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface ConnectServerUiEvent {
    data object Back : ConnectServerUiEvent
    data object Continue : ConnectServerUiEvent
    data object ScanInvite : ConnectServerUiEvent
    data object UseAnotherServer : ConnectServerUiEvent
}
