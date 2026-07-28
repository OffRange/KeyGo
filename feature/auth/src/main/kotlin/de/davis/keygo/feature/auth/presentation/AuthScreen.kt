package de.davis.keygo.feature.auth.presentation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.onFailure
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
            is BiometricRequest.CreateAccess -> {
                biometricCryptoController.requestCipher(
                    keyId = KeyId.BiometricVaultKek,
                    mode = request.cryptoMode
                ).onSuccess {
                    viewModel.createAccessWithUnwrappingCipher(request, it)
                }.onFailure {
                    Log.e("AuthScreen", "Failed to create cipher for biometric access: $it")
                    // TODO: show error
                }
            }

            BiometricRequest.Login -> {
                biometricUnlockAdapter.useAdapter {
                    biometricCryptoController.requestUnlockVault()
                }.onSuccess {
                    // Not currentOnSuccess() any more. Navigating straight from here would skip
                    // the v1 import, which every other way into the app starts, so the ViewModel
                    // takes it from here and navigates through navigationEvent, which is already
                    // collected above.
                    viewModel.onBiometricUnlockSucceeded()
                }
            }
        }

    }
    AuthContent(state = state, onEvent = viewModel::onEvent)
}