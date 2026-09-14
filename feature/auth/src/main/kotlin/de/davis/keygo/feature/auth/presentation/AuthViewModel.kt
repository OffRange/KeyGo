package de.davis.keygo.feature.auth.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.model.UnlockableByBiometricsResult
import de.davis.keygo.core.identity.domain.model.hasHardware
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockableByBiometricsUseCase
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.feature.auth.presentation.model.AuthUIEvent
import de.davis.keygo.legacy_migration.domain.model.MigrationResult
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.RunPendingMigrationUseCase
import de.davis.keygo.legacy_migration.domain.usecase.ValidateMainPasswordUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AuthViewModel(
    @InjectedParam private val authRoute: AuthRoute,
    unlockableByBiometrics: UnlockableByBiometricsUseCase,

    // ---- Migration ----
    private val hasV1MainPassword: HasMainPasswordUseCase,
    private val validateMainPassword: ValidateMainPasswordUseCase,
    private val runPendingMigration: RunPendingMigrationUseCase,
    // -------------------

    private val unlockWithBiometrics: UnlockWithBiometricsUseCase,
    private val unlockWithPassword: UnlockWithPasswordUseCase,
    private val createAllAccesses: CreateAccessUseCase,
) : ViewModel() {
    val hasPendingTotpImport: Boolean = authRoute.uri != null

    private val passwordTextFieldState = TextFieldState()

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val unlockableByBiometrics = unlockableByBiometrics()
            val shouldMigrate =
                if (unlockableByBiometrics is UnlockableByBiometricsResult.NoAccount)
                    hasV1MainPassword()
                else false

            val biometricsUsable = unlockableByBiometrics == UnlockableByBiometricsResult.Available

            _uiState.update {
                when {
                    shouldMigrate -> {
                        AuthState.Migrating(
                            passwordTextFieldState = passwordTextFieldState,
                            biometricsAvailable = unlockableByBiometrics.hasHardware(),
                        )
                    }

                    else -> AuthState.Login(
                        passwordTextFieldState = passwordTextFieldState,
                        biometricAuthenticationAvailable = biometricsUsable
                    )
                }
            }

            if (biometricsUsable && authRoute.showBiometricPromptIfPossible) requestBiometricLogin()
        }
    }

    private val navigationEventChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    private var migrationJob: Job? = null
    private var authJob: Job? = null

    fun onEvent(event: AuthUIEvent) {
        when (event) {
            is AuthUIEvent.RequestBiometricAuthentication ->
                if (uiState.value is AuthState.Login) requestBiometricLogin()

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
                            if (!validateMainPassword(password)) {
                                // Through the scope rather than straight to _uiState: loading
                                // writes the scope's state back when the block returns, so a
                                // direct write here would be overwritten and the user would see
                                // the spinner stop with no error against the field.
                                updateState {
                                    copyDefaultState(passwordError = UiFieldError.Incorrect)
                                }
                                return@loading
                            }

                            createAllAccesses(
                                password = password,
                                withBiometrics = state.biometricsAvailable && state.useBiometrics,
                            ).handleAuthenticationResult()
                        }
                    }
                }
            }

            AuthUIEvent.DismissBiometricResetNotice -> _uiState.update {
                if (it !is AuthState.Login) return@update it
                it.copy(showBiometricResetNotice = false)
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

            AuthUIEvent.RetryMigration -> performMigrationIfNeeded()

            AuthUIEvent.ContinueAfterMigration -> navigationEventChannel.trySend(Unit)
        }
    }

    private fun loading(
        setLoading: Boolean = true,
        block: suspend LoadingScope<AuthState.Interactable>.() -> Unit,
    ) {
        // One auth run at a time. Submit and the biometric callback both arrive here, and `onEvent`
        // gates on the state being interactable rather than on the loading flag, so nothing else
        // stops a second run. Two runs of account creation mint two accounts, two ARKs and two
        // vaults and the second overwrites the registry, which leaves the first vault wrapped under
        // an ARK that is no longer persisted anywhere.
        if (authJob?.isActive == true) return

        if (setLoading)
            _uiState.update {
                if (it !is AuthState.Interactable) return@update it
                it.copyDefaultState(loading = true)
            }

        authJob = viewModelScope.launch {
            val current = _uiState.value as? AuthState.Interactable ?: return@launch

            var sessionEstablished = false
            val scope = LoadingScope(
                state = current,
                onSuccess = { sessionEstablished = true },
            )
            scope.block()

            _uiState.update {
                if (it !is AuthState.Interactable) return@update it
                scope.updatedState.copyDefaultState(loading = false)
            }

            if (sessionEstablished) performMigrationIfNeeded()
        }
    }


    private fun requestBiometricLogin() {
        viewModelScope.launch {
            unlockWithBiometrics().onFailure {
                onBiometricUnlockFailed(it)
            }.onSuccess {
                performMigrationIfNeeded()
            }
        }
    }

    private fun onBiometricUnlockFailed(error: UnlockError) {
        if (error != UnlockError.BiometricEnrollmentReset) return

        _uiState.update { state ->
            when (state) {
                is AuthState.Login -> state.copy(
                    biometricAuthenticationAvailable = false,
                    showBiometricResetNotice = true,
                )

                else -> state
            }
        }
    }

    /**
     * Run after every path that establishes a session, which is the only moment the import can
     * happen: every secret it writes is re-encrypted under a key that hangs off the ARK.
     *
     * The marker is read here as well as inside the use case so the common case, an install with no
     * v1 migration pending, never flips the screen into an import it is not going to run.
     */
    private fun performMigrationIfNeeded() {
        // Retry is a button on a screen the user reaches after a failure, so it can be tapped twice
        // before the first run has published anything. Two concurrent imports would both read the
        // same v1 rows and both write them, so a tap that lands while one is running is dropped.
        if (migrationJob?.isActive == true) return

        migrationJob = viewModelScope.launch {
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

                is MigrationResult.Incomplete ->
                    _uiState.update { AuthState.MigrationFailed }
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

    /**
     * Records a state change without claiming a session was established, for the paths that have
     * something to say about the screen but have not authenticated anything.
     */
    fun updateState(transform: State.() -> State) {
        updatedState = updatedState.transform()
    }

    fun <S, E> Result<S, E>.handleAuthenticationResult(onFailure: State.(E) -> State = { this }) {
        onSuccess { onSuccess() }
            .onFailure { updatedState = updatedState.onFailure(it) }
    }
}