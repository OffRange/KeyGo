package de.davis.keygo.feature.account.presentation.setup.model

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.rust.username.UsernameError

internal sealed interface UsernameCheck {

    data object Idle : UsernameCheck
    data class Valid(val username: String) : UsernameCheck
    data class Invalid(val error: UsernameError) : UsernameCheck
    data class Rejected(val error: SyncError) : UsernameCheck
}
