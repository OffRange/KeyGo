package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface PasswordCheck {

    data object Idle : PasswordCheck
    data object Checking : PasswordCheck
    data object Incorrect : PasswordCheck
    data object Failed : PasswordCheck
}
