package de.davis.keygo.feature.account.presentation.setup.model

import de.davis.keygo.core.sync.domain.model.SyncError

internal sealed interface SignInCheck {

    data object Idle : SignInCheck
    data object Checking : SignInCheck
    data class Failed(val error: SyncError) : SignInCheck
}
