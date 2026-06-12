package de.davis.keygo.feature.backup.presentation.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupHubScreen(navigateToExport: () -> Unit) {
    val viewModel = koinViewModel<BackupHubViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            BackupHubEvent.NavigateToExport -> navigateToExport()
        }
    }


    BackupHubContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}