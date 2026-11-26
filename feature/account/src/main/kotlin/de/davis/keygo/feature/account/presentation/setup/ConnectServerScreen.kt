package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.transformation.rememberUsernameInputTransformation
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ConnectServerScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<ConnectServerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ConnectServerEvent.AccountCreated,
            ConnectServerEvent.SignedIn -> navigateUp()

            ConnectServerEvent.OpenInviteScanner -> Unit // TODO
        }
    }

    // Injected here rather than in the content, so the previewable composables below stay free
    // of Koin and need no fakes.
    ConnectServerContent(
        state = state,
        usernameInputTransformation = rememberUsernameInputTransformation(),
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
