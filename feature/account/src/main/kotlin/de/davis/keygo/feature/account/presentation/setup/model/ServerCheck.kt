package de.davis.keygo.feature.account.presentation.setup.model

import de.davis.keygo.core.sync.domain.model.SyncError

internal sealed interface ServerCheck {

    data object Idle : ServerCheck
    data object Checking : ServerCheck

    data class Found(
        val serverUrl: String,
        val version: String,
        val publicUrl: String,
        val inviteOnly: Boolean,
    ) : ServerCheck

    data object InvalidAddress : ServerCheck
    data class Failed(val error: SyncError) : ServerCheck
}
