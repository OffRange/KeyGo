package de.davis.keygo.auth.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.auth.presentation.model.BiometricRequest
import de.davis.keygo.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.migration.create_access.domain.usecase.ClearMainPasswordUseCase
import de.davis.keygo.migration.create_access.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.migration.create_access.domain.usecase.ValidateMainPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class AuthViewModel(
    savedStateHandle: SavedStateHandle,
    biometricAvailabilityRepository: BiometricAvailabilityRepository,
    wrappedKeyRepository: WrappedKeyRepository,

    // ---- Migration ----
    hasV1MainPassword: HasMainPasswordUseCase,
    private val validateMainPassword: ValidateMainPassword,
    private val clearMainPasswordUseCase: ClearMainPasswordUseCase,
    // -------------------

    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val unlockWithPassword: UnlockWithPasswordUseCase,
    private val createAllAccesses: CreateAccessUseCase,
) : ViewModel() {
    private val biometricChannel = Channel<BiometricRequest>()
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val authRoute = savedStateHandle.toRoute<RouteDestination.Auth>()

    private val passwordTextFieldState = TextFieldState()
    private val confirmPasswordTextFieldState = TextFieldState()

    private val _uiState =
        MutableStateFlow<AuthState>(AuthState.CreateAccess(passwordTextFieldState = passwordTextFieldState))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasAccess = wrappedKeyRepository.getPasswordWrappedKey().isSuccess()
            val hasAccessButShouldMigrate = if (!hasAccess) hasV1MainPassword()
            else false

            val isBiometricHardwareAvailable = biometricAvailabilityRepository.availability()
            val isBiometricCryptoSetupAvailable =
                hasAccess && wrappedKeyRepository.getBiometricWrappedKey().isSuccess()

            val biometricsUsable = isBiometricHardwareAvailable && isBiometricCryptoSetupAvailable
            if (biometricsUsable && authRoute.showBiometricPromptIfPossible) requestBiometricLogin()


            _uiState.update {
                when {
                    hasAccessButShouldMigrate -> {
                        AuthState.Migrating(
                            passwordTextFieldState = passwordTextFieldState,
                            biometricsAvailable = isBiometricHardwareAvailable,
                        )
                    }

                    hasAccess -> AuthState.Login(
                        passwordTextFieldState = passwordTextFieldState,
                        biometricAuthenticationAvailable = biometricsUsable
                    )

                    else -> {
                        observePasswordStrength()
                        observePasswordError()
                        observeConfirmPasswordError()

                        AuthState.CreateAccess(
                            passwordTextFieldState = passwordTextFieldState,
                            confirmPasswordTextFieldState = confirmPasswordTextFieldState,
                            biometricsAvailable = isBiometricHardwareAvailable
                        )
                    }
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordError() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { it.isBlank() }
            .distinctUntilChanged()
            .onEach { isBlank ->
                if (isBlank) return@onEach

                _uiState.update {
                    if (it !is AuthState.CreateAccess) return@update it
                    it.copy(passwordError = UIPasswordError.None)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun observeConfirmPasswordError() {
        snapshotFlow { passwordTextFieldState.text }
            .combine(snapshotFlow { confirmPasswordTextFieldState.text }) { password, confirm ->
                password == confirm
            }
            .distinctUntilChanged()
            .onEach { equal ->
                if (!equal) return@onEach

                _uiState.update {
                    if (it !is AuthState.CreateAccess) return@update it
                    it.copy(confirmPasswordError = UIPasswordError.None)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordStrength() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { passwordStrengthEstimator(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                _uiState.update {
                    if (it !is AuthState.CreateAccess) return@update it
                    it.copy(score = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private val navigationEventChannel = Channel<Unit>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> if (uiState.value is AuthState.Login) requestBiometricLogin()

            AuthUIEvent.Submit -> {
                val state = _uiState.value
                val password = state.passwordTextFieldState.text.toString()
                when (state) {
                    is AuthState.Login -> {
                        loading(setLoading = password.isNotBlank()) {
                            unlockWithPassword(
                                password = password
                            ).handleAuthenticationResult {
                                state.copyDefaultState(passwordError = UIPasswordError.Incorrect)
                            }
                        }
                    }

                    is AuthState.Migrating -> {
                        loading {
                            validateMainPassword(password).asResult(Unit)
                                .onFailure {
                                    _uiState.update {
                                        it.copyDefaultState(passwordError = UIPasswordError.Incorrect)
                                    }
                                }.onSuccess {
                                    clearMainPasswordUseCase()
                                    createPasswordOrBiometricAccess(state, password)
                                }
                        }
                    }

                    is AuthState.CreateAccess -> {
                        val errorFreeState = state.copy(
                            passwordError = UIPasswordError.None,
                            confirmPasswordError = UIPasswordError.None
                        )

                        if (password.isBlank()) {
                            _uiState.update {
                                errorFreeState.copy(passwordError = UIPasswordError.Empty)
                            }
                            return
                        }
                        val confirmedPassword =
                            errorFreeState.confirmPasswordTextFieldState.text.toString()
                        if (password != confirmedPassword) {
                            _uiState.update {
                                errorFreeState.copy(confirmPasswordError = UIPasswordError.Incorrect)
                            }
                            return
                        }

                        createPasswordOrBiometricAccess(errorFreeState, password)
                    }
                }
            }

            AuthUIEvent.CloseMigrationDialog -> {
                _uiState.update {
                    if (it !is AuthState.Migrating) return@update it
                    it.copy(showMigrationDialog = false)
                }
            }

            is AuthUIEvent.ToggleUseBiometrics -> {
                _uiState.update {
                    if (it !is AuthState.BiometricAuthState) return@update it
                    it.copyBiometricState(useBiometrics = event.checked)
                }
            }
        }
    }

    private fun createPasswordOrBiometricAccess(
        authState: AuthState.BiometricAuthState,
        password: String
    ) {
        if (!authState.biometricsAvailable || !authState.useBiometrics) {
            loading {
                createAllAccesses(password = password).handleAuthenticationResult()
            }

            return
        }

        biometricChannel.trySend(BiometricRequest.CreateAccess(password))
    }

    private fun requestBiometricLogin() {
        biometricChannel.trySend(BiometricRequest.Login)
    }

    private fun loading(
        setLoading: Boolean = true,
        block: suspend LoadingScope<AuthState>.() -> Unit
    ) {
        if (setLoading)
            _uiState.update { it.copyDefaultState(loading = true) }

        viewModelScope.launch {
            _uiState.update {
                LoadingScope(
                    state = it,
                    onSuccess = { navigationEventChannel.trySend(Unit) },
                ).apply {
                    block()
                }.updatedState.copyDefaultState(loading = false)
            }
        }
    }

    fun createAccessWithUnwrappingCipher(
        createAccessRequest: BiometricRequest.CreateAccess,
        cipher: Cipher
    ) {
        loading {
            createAllAccesses(
                password = createAccessRequest.password,
                biometricCipher = cipher
            ).handleAuthenticationResult()
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