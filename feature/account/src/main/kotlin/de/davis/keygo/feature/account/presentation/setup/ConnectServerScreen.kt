package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ConnectServerScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<ConnectServerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ConnectServerEvent.AccountCreated -> navigateUp()
            ConnectServerEvent.OpenInviteScanner -> Unit // TODO
        }
    }

    ConnectServerContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
