package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(navigate: (/*TODO*/) -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
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