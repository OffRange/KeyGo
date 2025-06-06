package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.compose.scope.rememberKoinScope
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun AuthScreen(navigate: (/*TODO*/) -> Unit) {
    val session = koinInject<Session>()
    val scope = rememberKoinScope(session.scope)
    val viewModel = koinViewModel<AuthViewModel>(scope = scope)
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