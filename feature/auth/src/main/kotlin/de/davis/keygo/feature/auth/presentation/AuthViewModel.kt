package de.davis.keygo.feature.auth.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.feature.auth.presentation.model.AuthUIEvent
import de.davis.keygo.feature.auth.presentation.model.BiometricRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AuthViewModel(
    savedStateHandle: SavedStateHandle,
    biometricAvailabilityRepository: BiometricAvailabilityRepository,
    accountRepository: AccountRepository,

    private val unlockWithPassword: UnlockWithPasswordUseCase,
    private val createAllAccesses: CreateAccessUseCase,
) : ViewModel() {
    private val biometricChannel = Channel<BiometricRequest>()
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val authRoute = savedStateHandle.toRoute<AuthRoute>()

    private val passwordTextFieldState = TextFieldState()

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeAccount = accountRepository.getOrNull()
            val hasAccess = activeAccount != null

            val isBiometricHardwareAvailable = biometricAvailabilityRepository.availability()
            val isBiometricCryptoSetupAvailable =
                hasAccess && activeAccount.biometricWrappedArk != null

            val biometricsUsable = isBiometricHardwareAvailable && isBiometricCryptoSetupAvailable
            if (biometricsUsable && authRoute.showBiometricPromptIfPossible) requestBiometricLogin()

            _uiState.update {
                AuthState.Login(
                    passwordTextFieldState = passwordTextFieldState,
                    biometricAuthenticationAvailable = biometricsUsable
                )
            }
        }
    }

    private val navigationEventChannel = Channel<Unit>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> if (uiState.value is AuthState.Login) requestBiometricLogin()

            AuthUIEvent.Submit -> {
                val state = _uiState.value as? AuthState.Login ?: return
                val password = state.passwordTextFieldState.text.toString()
                loading(setLoading = password.isNotBlank()) {
                    unlockWithPassword(
                        password = password
                    ).handleAuthenticationResult {
                        state.copy(passwordError = UiFieldError.Incorrect)
                    }
                }
            }
        }
    }

    private fun requestBiometricLogin() {
        biometricChannel.trySend(BiometricRequest.Login)
    }

    private fun loading(
        setLoading: Boolean = true,
        block: suspend LoadingScope<AuthState.Login>.() -> Unit
    ) {
        if (setLoading) _uiState.update {
            if (it !is AuthState.Login) return@update it
            it.copy(passwordError = null)
        }

        viewModelScope.launch {
            _uiState.update {
                if (it !is AuthState.Login) return@update it

                LoadingScope(
                    state = it,
                    onSuccess = { navigationEventChannel.trySend(Unit) },
                ).apply {
                    block()
                }.updatedState.copy(loading = false)
            }
        }
    }
}

private class LoadingScope<State>(
    state: State,
    private val onSuccess: () -> Unit,
) {
    var updatedState: State = state
        private set

    fun <S, E> Result<S, E>.handleAuthenticationResult(onFailure: State.(E) -> State = { this }) {
        onSuccess { onSuccess() }
            .onFailure { updatedState = updatedState.onFailure(it) }
    }
}