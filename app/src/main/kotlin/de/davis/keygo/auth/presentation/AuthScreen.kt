package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(navigate: (/*TODO*/) -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.navigationEvent) {
        navigate()
    }

    BiometricAuthHandler(
        flow = viewModel.biometricRequests,
        onAuthenticationSucceeded = {
            viewModel.onEvent(AuthUIEvent.BiometricSuccess(it))
        },
        onAuthenticationError = { _, _ ->
            viewModel.onEvent(AuthUIEvent.BiometricError)
        },
        onAuthenticationFailed = {
            viewModel.onEvent(AuthUIEvent.BiometricFailure)
        }
    )

    AuthContent(state = state, onEvent = viewModel::onEvent)
}