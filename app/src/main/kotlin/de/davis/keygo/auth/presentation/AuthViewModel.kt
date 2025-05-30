package de.davis.keygo.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.usecase.GetBiometricAvailabilityUseCase
import de.davis.keygo.auth.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.auth.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.core.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.UIText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    getBiometricAvailability: GetBiometricAvailabilityUseCase,
    private val prepareBiometricCipher: PrepareBiometricCipherUseCase,
    private val unlockWithBiometrics: UnlockWithBiometricsUseCase,
    private val snackbarManager: SnackbarManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state = _state.onStart {
        val isBiometricsAvailable = getBiometricAvailability() == BiometricAvailability.Available
        _state.update {
            it.copy(
                biometricsAvailable = isBiometricsAvailable
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState()
    )

    private val biometricRequestChannel = Channel<BiometricRequest>()
    val biometricRequests = biometricRequestChannel.receiveAsFlow()


    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> {
                viewModelScope.launch {
                    prepareBiometricCipher(mode = CryptographicMode.Unwrap)
                        .onSuccess {
                            biometricRequestChannel.send(BiometricRequest.Class3(it))
                        }
                        .onFailure {
                            snackbarManager.sendMessage(SnackbarMessage(UIText.RawString("$it") /*TODO*/))
                        }
                }
            }

            is AuthUIEvent.PasswordChanged -> updateState { it.copy(password = event.password) }
            AuthUIEvent.Submit -> {
                viewModelScope.launch {
                    // TODO
                }
            }

            AuthUIEvent.BiometricError -> {}
            AuthUIEvent.BiometricFailure -> {}
            is AuthUIEvent.BiometricSuccess -> {
                val cipher = event.result.cryptoObject?.cipher ?: return
                viewModelScope.launch {
                    unlockWithBiometrics(cipher)
                        .onSuccess {
                            updateState { it.copy(authEvent = AuthEvent.Success) }
                        }.onFailure {
                            updateState { it.copy(authEvent = AuthEvent.Failure) }
                        }
                }

            }
        }
    }

    private fun updateState(block: (AuthState) -> AuthState) {
        _state.update(block)
    }
}