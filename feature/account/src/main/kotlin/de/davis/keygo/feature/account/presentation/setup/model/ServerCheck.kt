package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface ServerCheck {

    data object Idle : ServerCheck
    data object Checking : ServerCheck

    data class Found(
        val version: String,
        val publicUrl: String,
        val inviteOnly: Boolean,
    ) : ServerCheck

    data class Failed(val reason: Reason) : ServerCheck {

        enum class Reason {
            Unreachable,
            NotKeyGo,
        }
    }
}
