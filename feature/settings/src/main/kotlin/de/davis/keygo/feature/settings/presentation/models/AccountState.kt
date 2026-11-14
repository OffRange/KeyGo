package de.davis.keygo.feature.settings.presentation.models

import androidx.compose.runtime.Stable

@Stable
internal sealed interface AccountState {
    data class Connected(
        val username: String,
        val server: String
    ) : AccountState

    data object Local : AccountState
}
