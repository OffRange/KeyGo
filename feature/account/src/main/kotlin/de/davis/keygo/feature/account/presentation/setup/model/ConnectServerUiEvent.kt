package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface ConnectServerUiEvent {
    data object Back : ConnectServerUiEvent
    data object Continue : ConnectServerUiEvent
    data object Retry : ConnectServerUiEvent
    data class ChooseAccount(val choice: AccountChoice) : ConnectServerUiEvent
    data class SchemeChanged(val scheme: String?) : ConnectServerUiEvent
    data object ScanInvite : ConnectServerUiEvent
    data object UseAnotherServer : ConnectServerUiEvent
}
