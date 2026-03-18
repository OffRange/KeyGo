package de.davis.keygo.auth.presentation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.auth.presentation.model.BiometricRequest
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.asUIText
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
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

    val snackbarManager = LocalSnackbarManager.current

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
                    snackbarManager.sendMessage(
                        SnackbarMessage(
                            message = "Failed to create cipher for biometric access".asUIText(),
                        )
                    )
                }
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