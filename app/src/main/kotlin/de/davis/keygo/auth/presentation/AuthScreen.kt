package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.davis.keygo.auth.domain.rememberBiometricManager
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AuthScreen(navigate: (/*TODO*/) -> Unit) {
    val biometricManager = rememberBiometricManager()
    val viewModel = koinViewModel<AuthViewModel> { parametersOf(biometricManager) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.authEvent) {
        when (state.authEvent) {
            AuthEvent.None -> viewModel.onEvent(AuthUIEvent.RequestBiometricAuthentication)
            AuthEvent.Failure -> {}
            AuthEvent.Success -> navigate()
        }
    }

    AuthContent(state = state, onEvent = viewModel::onEvent)
}