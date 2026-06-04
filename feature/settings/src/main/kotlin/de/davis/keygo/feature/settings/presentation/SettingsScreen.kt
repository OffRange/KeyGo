package de.davis.keygo.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen() {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onEvent = {}
    )
}