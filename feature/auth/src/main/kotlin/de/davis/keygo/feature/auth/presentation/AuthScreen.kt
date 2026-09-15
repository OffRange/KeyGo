package de.davis.keygo.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AuthScreen(route: AuthRoute, onSuccess: () -> Unit) {
    val viewModel = koinViewModel<AuthViewModel> { parametersOf(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val currentOnSuccess by rememberUpdatedState(onSuccess)

    ObserveAsEvents(viewModel.navigationEvent) {
        currentOnSuccess()
    }

    AuthContent(
        state = state,
        onEvent = viewModel::onEvent,
        hasPendingTotpImport = viewModel.hasPendingTotpImport,
    )
}