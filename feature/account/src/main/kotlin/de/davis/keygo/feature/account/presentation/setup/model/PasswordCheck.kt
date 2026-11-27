package de.davis.keygo.feature.account.presentation.setup.model

import de.davis.keygo.core.sync.domain.model.SyncError

internal sealed interface PasswordCheck {

    data object Idle : PasswordCheck
    data object Checking : PasswordCheck
    data object Incorrect : PasswordCheck
    data object Failed : PasswordCheck
    data class RegisterFailed(val error: SyncError) : PasswordCheck
}
