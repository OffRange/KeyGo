package de.davis.keygo.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.auth.domain.BiometricManager
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.model.BiometricResult
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.domain.Service
import de.davis.keygo.core.domain.error.ValidationError
import de.davis.keygo.core.domain.executeIfAvailable
import de.davis.keygo.core.domain.isAvailable
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.usecase.ValidateMainPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val biometricManagerService: Service<BiometricManager>,
    private val validateMainPassword: ValidateMainPassword
) : ViewModel() {

    private val _state =
        MutableStateFlow(AuthState(biometricsAvailable = biometricManagerService.isAvailable()))
    val state = _state.asStateFlow()


    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> {
                viewModelScope.launch {
                    biometricManagerService.executeIfAvailable {
                        requestBiometric(BiometricRequest.Class2).collect { biometricResult ->
                            updateState {
                                when (biometricResult) {
                                    BiometricResult.Success -> it.copy(authEvent = AuthEvent.Success)
                                    else -> it.copy(authEvent = AuthEvent.Failure)
                                }
                            }
                        }
                    }
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
        }
    }

    private fun updateState(block: (AuthState) -> AuthState) {
        _state.update(block)
    }
}