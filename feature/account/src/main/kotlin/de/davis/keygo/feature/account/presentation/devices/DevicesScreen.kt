package de.davis.keygo.feature.account.presentation.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun DevicesScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<DevicesViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(DevicesUiEvent.OnResumed)
        onPauseOrDispose {}
    }

    DevicesContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
