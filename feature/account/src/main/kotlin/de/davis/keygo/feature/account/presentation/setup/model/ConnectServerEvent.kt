package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface ConnectServerEvent {
    data object OpenInviteScanner : ConnectServerEvent
    data object AccountCreated : ConnectServerEvent
}
