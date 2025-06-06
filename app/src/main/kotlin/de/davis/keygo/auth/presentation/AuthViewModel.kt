package de.davis.keygo.auth.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.usecase.CreateAccessUseCase
import de.davis.keygo.auth.domain.usecase.GetBiometricCryptoSetupAvailabilityUseCase
import de.davis.keygo.auth.domain.usecase.GetBiometricHardwareAvailabilityUseCase
import de.davis.keygo.auth.domain.usecase.HasValidAccessUseCase
import de.davis.keygo.auth.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.auth.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.auth.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.auth.presentation.model.AuthEvent
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.UIText
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class AuthViewModel(
    getBiometricCryptoSetupAvailability: GetBiometricCryptoSetupAvailabilityUseCase,
    getBiometricHardwareAvailability: GetBiometricHardwareAvailabilityUseCase,
    hasValidAccessUseCase: HasValidAccessUseCase,
    private val estimateStrength: EstimatePasswordStrengthUseCase,
    private val prepareBiometricCipher: PrepareBiometricCipherUseCase,
    private val unlockWithBiometrics: UnlockWithBiometricsUseCase,
    private val unlockWithPasswordUseCase: UnlockWithPasswordUseCase,
    private val createAccess: CreateAccessUseCase,
    private val snackbarManager: SnackbarManager
) : ViewModel() {

    private val passwordTextFieldState = TextFieldState()
    private val confirmPasswordTextFieldState = TextFieldState()

    private val _state = MutableStateFlow(AuthState())
    val state = _state.onStart {
        val hasAccess = hasValidAccessUseCase()
        updateState {
            it.copy(
                authMode = when {
                    hasAccess -> AuthState.Mode.Login(
                        passwordTextFieldState = passwordTextFieldState
                    )

                    else -> AuthState.Mode.Register(
                        passwordTextFieldState = passwordTextFieldState,
                        confirmPasswordTextFieldState = confirmPasswordTextFieldState
                    )
                }
            )
        }

        val isBiometricHardwareAvailable =
            getBiometricHardwareAvailability() == BiometricAvailability.Available
        if (!hasAccess) {
            observePasswordStrength()

            updateState {
                it.copy(biometricsAvailable = isBiometricHardwareAvailable)
            }
            return@onStart
        }

        val isBiometricCryptoSetupAvailable =
            getBiometricCryptoSetupAvailability() == BiometricAvailability.Available

        val canAuthenticate = isBiometricHardwareAvailable && isBiometricCryptoSetupAvailable
        if (canAuthenticate) requestBiometricAuthentication()

        updateState {
            it.copy(biometricsAvailable = canAuthenticate)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState()
    )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordStrength() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { estimateStrength(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                updateState {
                    it.copy(authMode = it.authMode.copyDefaults(score = score))
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private val biometricRequestChannel = Channel<BiometricRequest>()
    val biometricRequests = biometricRequestChannel.receiveAsFlow()


    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> if (state.value.authMode is AuthState.Mode.Login) requestBiometricAuthentication()

            AuthUIEvent.Submit -> {
                val state = state.value
                when (state.authMode) {
                    is AuthState.Mode.Login -> {
                        val password = state.authMode.passwordTextFieldState.text.toString()
                        if (password.isNotBlank())
                            updateState { it.copy(loading = true) }

                        viewModelScope.launch {
                            unlockWithPasswordUseCase(
                                password = password
                            ).onSuccess {
                                updateState { it.copy(authEvent = AuthEvent.Success) }
                            }.onFailure {
                                updateState {
                                    it.copy(
                                        loading = false,
                                        authEvent = AuthEvent.Failure,
                                        authMode = it.authMode.copyDefaults(passwordError = UIPasswordError.Incorrect)
                                    )
                                }
                            }
                        }
                    }

                    is AuthState.Mode.Register -> {
                        val password = state.authMode.passwordTextFieldState.text.toString()
                        if (password.isBlank()) {
                            updateState {
                                it.copy(
                                    authMode = state.authMode.copyDefaults(passwordError = UIPasswordError.Empty)
                                )
                            }
                            return
                        }
                        val confirmedPassword =
                            state.authMode.confirmPasswordTextFieldState.text.toString()
                        if (password != confirmedPassword) {
                            updateState {
                                it.copy(
                                    authMode = state.authMode.copy(
                                        confirmPasswordError = UIPasswordError.Incorrect
                                    )
                                )
                            }
                            return
                        }

                        if (!state.biometricsAvailable || !state.authMode.useBiometrics) {
                            updateState { it.copy(loading = true) }
                            viewModelScope.launch {
                                createAccess(password = password)
                                    .onSuccess {
                                        updateState { it.copy(authEvent = AuthEvent.Success) }
                                    }.onFailure {
                                        updateState {
                                            it.copy(
                                                authEvent = AuthEvent.Failure,
                                                loading = false
                                            )
                                        }
                                    }
                            }

                            return
                        }

                        requestBiometricAuthentication(mode = CryptographicMode.Wrap)
                    }
                }
            }

            AuthUIEvent.BiometricError -> {}
            AuthUIEvent.BiometricFailure -> {}
            is AuthUIEvent.BiometricSuccess -> {
                val cipher = event.result.cryptoObject?.cipher ?: return
                updateState { it.copy(loading = true) }
                val state = state.value
                if (state.authMode is AuthState.Mode.Login) {
                    viewModelScope.launch {
                        unlockWithBiometrics(cipher)
                            .onSuccess {
                                updateState { it.copy(authEvent = AuthEvent.Success) }
                            }.onFailure {
                                updateState {
                                    it.copy(
                                        authEvent = AuthEvent.Failure,
                                        loading = false
                                    )
                                }
                            }
                    }
                    return
                }

                viewModelScope.launch {
                    createAccess(
                        password = state.authMode.passwordTextFieldState.text.toString(),
                        biometricCipher = cipher
                    ).onSuccess {
                        updateState { it.copy(authEvent = AuthEvent.Success) }
                    }.onFailure {
                        updateState { it.copy(authEvent = AuthEvent.Failure, loading = false) }
                    }
                }
            }

            is AuthUIEvent.ToggleUseBiometrics -> {
                updateState {
                    val mode = it.authMode as? AuthState.Mode.Register ?: return@updateState it
                    it.copy(authMode = mode.copy(useBiometrics = event.checked))
                }
            }
        }
    }

    private fun requestBiometricAuthentication(mode: CryptographicMode = CryptographicMode.Unwrap) {
        viewModelScope.launch {
            prepareBiometricCipher(mode = mode)
                .onSuccess {
                    biometricRequestChannel.send(BiometricRequest.Class3(it))
                }
                .onFailure {
                    snackbarManager.sendMessage(SnackbarMessage(UIText.RawString("$it") /*TODO*/))
                }
        }
    }

    private fun updateState(block: (AuthState) -> AuthState) {
        _state.update(block)
    }
}