package de.davis.keygo.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.auth.presentation.model.BiometricRequest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(onSuccess: () -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val currentOnSuccess by rememberUpdatedState(onSuccess)

    ObserveAsEvents(viewModel.navigationEvent) {
        currentOnSuccess()
    }

    val biometricCryptoController = rememberBiometricCryptoController()
    val biometricUnlockAdapter = rememberBiometricUnlockAdapter()

    ObserveAsEvents(viewModel.biometricFlow) { request ->
        when (request) {
            BiometricRequest.Login -> {
                biometricUnlockAdapter.useAdapter {
                    biometricCryptoController.requestUnlockVault()
                }.onSuccess {
                    currentOnSuccess()
                }
            }
        }

    }
    AuthContent(state = state, onEvent = viewModel::onEvent)
}