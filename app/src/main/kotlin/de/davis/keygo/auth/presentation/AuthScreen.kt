package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.identity.biometric.domain.model.BiometricRequest
import de.davis.keygo.core.identity.biometric.presentation.BiometricPromptSupport
import de.davis.keygo.core.identity.biometric.presentation.LocalBiometricManager
import de.davis.keygo.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(onSuccess: () -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.navigationEvent) {
        onSuccess()
    }

    BiometricPromptSupport {
        val biometricManager = LocalBiometricManager.current

        ObserveAsEvents(viewModel.biometricRequests) {
            when (it) {
                is BiometricRequest.Class3 -> {
                    val result = biometricManager.authenticate(it)
                    viewModel.onBiometricResult(result)
                }
            }
        }

        AuthContent(state = state, onEvent = viewModel::onEvent)
    }
}