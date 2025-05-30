package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.core.domain.Session
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.compose.scope.rememberKoinScope

@Composable
fun AuthScreen(navigate: (/*TODO*/) -> Unit) {
    val session = koinInject<Session>()
    val viewModel = koinViewModel<AuthViewModel>(scope = rememberKoinScope(session.scope))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.authEvent) {
        when (state.authEvent) {
            AuthEvent.None -> viewModel.onEvent(AuthUIEvent.RequestBiometricAuthentication)
            AuthEvent.Failure -> {}
            AuthEvent.Success -> navigate()
        }
    }

    BiometricAuthHandler(
        flow = viewModel.biometricRequests,
        onAuthenticationSucceeded = {
            viewModel.onEvent(AuthUIEvent.BiometricSuccess(it))
        },
        onAuthenticationError = { errorCode, errString ->
            viewModel.onEvent(AuthUIEvent.BiometricError)
        },
        onAuthenticationFailed = {
            viewModel.onEvent(AuthUIEvent.BiometricFailure)
        }
    )

    AuthContent(state = state, onEvent = viewModel::onEvent)
}