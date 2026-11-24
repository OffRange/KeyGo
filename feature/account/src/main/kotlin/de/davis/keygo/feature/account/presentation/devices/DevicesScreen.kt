package de.davis.keygo.feature.account.presentation.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.devices.model.DevicesEvent
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun DevicesScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<DevicesViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarManager = LocalSnackbarManager.current

    ObserveAsEvents(viewModel.event) { event ->
        when (event) {
            DevicesEvent.RefreshFailed -> snackbarManager.sendMessage(
                SnackbarMessage(message = ResourceString(R.string.devices_refresh_failed)),
            )

            is DevicesEvent.RemoveFailed -> snackbarManager.sendMessage(
                SnackbarMessage(message = ResourceString(event.error.messageRes)),
            )
        }
    }

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
