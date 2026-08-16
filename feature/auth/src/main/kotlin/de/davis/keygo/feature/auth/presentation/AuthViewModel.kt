package de.davis.keygo.feature.auth.presentation

import android.util.Log
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
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.feature.auth.presentation.model.AuthUIEvent
import de.davis.keygo.feature.auth.presentation.model.BiometricRequest
import de.davis.keygo.legacy_migration.domain.model.MigrationResult
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.RunPendingMigrationUseCase
import de.davis.keygo.legacy_migration.domain.usecase.ValidateMainPasswordUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import javax.crypto.Cipher

@KoinViewModel
internal class AuthViewModel(
    savedStateHandle: SavedStateHandle,
    biometricAvailabilityRepository: BiometricAvailabilityRepository,
    accountRepository: AccountRepository,

    // ---- Migration ----
    private val hasV1MainPassword: HasMainPasswordUseCase,
    private val validateMainPassword: ValidateMainPasswordUseCase,
    private val runPendingMigration: RunPendingMigrationUseCase,
    // -------------------

    private val unlockWithPassword: UnlockWithPasswordUseCase,
    private val createAllAccesses: CreateAccessUseCase,
) : ViewModel() {
    private val biometricChannel = Channel<BiometricRequest>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val authRoute = savedStateHandle.toRoute<AuthRoute>()

    val hasPendingTotpImport: Boolean = authRoute.uri != null

    private val passwordTextFieldState = TextFieldState()

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeAccount = accountRepository.getOrNull()
            val hasAccess = activeAccount != null
            val shouldMigrate = if (!hasAccess) hasV1MainPassword() else false

            val isBiometricHardwareAvailable = biometricAvailabilityRepository.availability()
            val isBiometricCryptoSetupAvailable =
                hasAccess && activeAccount.biometricWrappedArk != null

            val biometricsUsable = isBiometricHardwareAvailable && isBiometricCryptoSetupAvailable
            if (biometricsUsable && authRoute.showBiometricPromptIfPossible) requestBiometricLogin()


            _uiState.update {
                when {
                    shouldMigrate -> {
                        AuthState.Migrating(
                            passwordTextFieldState = passwordTextFieldState,
                            biometricsAvailable = isBiometricHardwareAvailable,
                        )
                    }

                    else -> AuthState.Login(
                        passwordTextFieldState = passwordTextFieldState,
                        biometricAuthenticationAvailable = biometricsUsable
                    )
                }
            }
        }
    }

    private val navigationEventChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication -> if (uiState.value is AuthState.Login) requestBiometricLogin()

            AuthUIEvent.Submit -> {
                val state = _uiState.value as? AuthState.Interactable ?: return
                val password = state.passwordTextFieldState.text.toString()
                when (state) {
                    is AuthState.Login -> {
                        loading(setLoading = password.isNotBlank()) {
                            unlockWithPassword(
                                password = password
                            ).handleAuthenticationResult {
                                copyDefaultState(passwordError = UiFieldError.Incorrect)
                            }
                        }
                    }

                    is AuthState.Migrating -> {
                        loading {
                            validateMainPassword(password).asResult(Unit)
                                .onFailure {
                                    _uiState.update {
                                        if (it !is AuthState.Interactable) return@update it
                                        it.copyDefaultState(passwordError = UiFieldError.Incorrect)
                                    }
                                }.onSuccess {
                                    createPasswordOrBiometricAccess(state, password)
                                }
                        }
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
                    if (it !is AuthState.Migrating) return@update it
                    it.copy(useBiometrics = event.checked)
                }
            }

            AuthUIEvent.RetryMigration -> onSessionEstablished()

            AuthUIEvent.ContinueAfterMigration -> navigationEventChannel.trySend(Unit)
        }
    }

    private fun createPasswordOrBiometricAccess(
        authState: AuthState.Migrating,
        password: String
    ) {
        if (!authState.biometricsAvailable || !authState.useBiometrics) {
            executeCreateAccess(password = password)
            return
        }

        biometricChannel.trySend(BiometricRequest.CreateAccess(password))
    }

    private fun requestBiometricLogin() {
        biometricChannel.trySend(BiometricRequest.Login)
    }

    private fun loading(
        setLoading: Boolean = true,
        block: suspend LoadingScope<AuthState.Interactable>.() -> Unit,
    ) {
        if (setLoading)
            _uiState.update {
                if (it !is AuthState.Interactable) return@update it
                it.copyDefaultState(loading = true)
            }

        viewModelScope.launch {
            val current = _uiState.value as? AuthState.Interactable ?: return@launch

            var sessionEstablished = false
            val scope = LoadingScope(
                state = current,
                onSuccess = { sessionEstablished = true },
            )
            scope.block()

            _uiState.update { scope.updatedState.copyDefaultState(loading = false) }

            if (sessionEstablished) onSessionEstablished()
        }
    }

    /**
     * Run after every path that establishes a session, which is the only moment the import can
     * happen: every secret it writes is re-encrypted under a key that hangs off the ARK.
     *
     * The marker is read here as well as inside the use case so the common case, an install with no
     * v1 migration pending, never flips the screen into an import it is not going to run.
     */
    fun onSessionEstablished() {
        viewModelScope.launch {
            if (!hasV1MainPassword()) {
                navigationEventChannel.trySend(Unit)
                return@launch
            }

            _uiState.update { AuthState.ImportingLegacyData }

            when (val result = runPendingMigration()) {
                MigrationResult.NotPending -> navigationEventChannel.trySend(Unit)

                is MigrationResult.Completed ->
                    if (result.skippedItems == 0) navigationEventChannel.trySend(Unit)
                    else _uiState.update { AuthState.MigrationSummary(result.skippedItems) }

                is MigrationResult.Incomplete -> {
                    Log.e(TAG, "v1 import did not finish", result.cause)
                    _uiState.update { AuthState.MigrationFailed }
                }
            }
        }
    }

    fun executeCreateAccess(
        password: String,
        cipher: Cipher? = null,
    ) {
        loading {
            createAllAccesses(
                password = password,
                biometricCipher = cipher,
            ).handleAuthenticationResult()
        }
    }
}

private const val TAG = "AuthViewModel"

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