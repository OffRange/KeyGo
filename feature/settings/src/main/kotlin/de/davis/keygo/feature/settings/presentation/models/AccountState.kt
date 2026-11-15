package de.davis.keygo.feature.settings.presentation.models

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
internal sealed interface AccountState {
    data class Connected(
        val username: String,
        val server: String
    ) : AccountState

    data object Local : AccountState
}

internal val AccountState.badgeColor: Color
    @Composable
    get() = if (this is AccountState.Connected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primaryContainer

