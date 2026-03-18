package de.davis.keygo.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.BiometricRequest
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(onSuccess: () -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.navigationEvent) {
        onSuccess()
    }

    val biometricCryptoController = rememberBiometricCryptoController()
    val biometricUnlockAdapter = rememberBiometricUnlockAdapter()


    ObserveAsEvents(viewModel.biometricFlow) { request ->
        when (request) {
            is BiometricRequest.CreateAccess -> {
                biometricCryptoController.requestCipher(
                    keyId = KeyId.BiometricVaultKek,
                    mode = request.cryptoMode
                ).onSuccess {
                    viewModel.createAccessWithUnwrappingCipher(request, it)
                } // TODO: handle errors
            }

            BiometricRequest.Login -> {
                biometricUnlockAdapter.useAdapter {
                    biometricCryptoController.requestUnlockVault()
                }.onSuccess {
                    onSuccess()
                }
            }
        }

    }
    AuthContent(state = state, onEvent = viewModel::onEvent)
}