package de.davis.keygo.feature.password_health.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordHealthScreen() {
    val viewModel = koinViewModel<PasswordHealthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PasswordHealthContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}