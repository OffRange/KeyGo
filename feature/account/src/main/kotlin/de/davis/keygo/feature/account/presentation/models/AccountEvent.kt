package de.davis.keygo.feature.account.presentation.models

internal sealed interface AccountEvent {

    data object NavigateToChangePassword : AccountEvent
    data object NavigateBack : AccountEvent
}