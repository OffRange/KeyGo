package de.davis.keygo.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.auth.domain.model.BiometricCapability
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.repository.CheckBiometricCapabilityRepository
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.domain.error.ValidationError
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.usecase.ValidateMainPassword
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    checkBiometricCapability: CheckBiometricCapabilityRepository,
    private val validateMainPassword: ValidateMainPassword
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthState(
            biometricsAvailable = checkBiometricCapability.getCapability() == BiometricCapability.Available
        )
    )
    val state = _state.asStateFlow()

    private val biometricRequestChannel = Channel<BiometricRequest>()
    val biometricRequests = biometricRequestChannel.receiveAsFlow()


    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> {
                viewModelScope.launch {
                    biometricRequestChannel.send(BiometricRequest.Class2)
                }
            }

            is AuthUIEvent.PasswordChanged -> updateState { it.copy(password = event.password) }
            AuthUIEvent.Submit -> {
                viewModelScope.launch {
                    validateMainPassword(state.value.password)
                        .onSuccess {
                            updateState {
                                it.copy(
                                    authEvent = AuthEvent.Success,
                                    passwordError = UIPasswordError.None
                                )
                            }
                        }
                        .onFailure { error ->
                            when (error) {
                                ValidationError.NoMatch -> updateState {
                                    it.copy(
                                        authEvent = AuthEvent.Failure,
                                        passwordError = UIPasswordError.Incorrect
                                    )
                                }
                            }
                        }
                }
            }

            AuthUIEvent.BiometricError -> {}
            AuthUIEvent.BiometricFailure -> {}
            is AuthUIEvent.BiometricSuccess -> updateState { it.copy(authEvent = AuthEvent.Success) }
        }
    }

    private fun updateState(block: (AuthState) -> AuthState) {
        _state.update(block)
    }
}