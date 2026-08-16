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
import kotlinx.coroutines.Job
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

    private var migrationJob: Job? = null

    private var authJob: Job? = null

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
                                    // Through the scope rather than straight to _uiState: loading
                                    // writes the scope's state back when the block returns, so a
                                    // direct write here would be overwritten and the user would see
                                    // the spinner stop with no error against the field.
                                    updateState {
                                        copyDefaultState(passwordError = UiFieldError.Incorrect)
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

    /**
     * Runs inside the caller's [loading] rather than starting a second one, so the screen stays
     * loading until the account actually exists. A nested [loading] returned as soon as it had
     * launched, which wrote `loading = false` back while key derivation was still running and
     * re-enabled Submit for the whole of it.
     */
    private suspend fun LoadingScope<AuthState.Interactable>.createPasswordOrBiometricAccess(
        authState: AuthState.Migrating,
        password: String,
    ) {
        if (authState.biometricsAvailable && authState.useBiometrics) {
            // Handed to the prompt. AuthScreen starts a fresh run with the cipher once the user has
            // answered, and by then this one has finished, so the guard in loading does not eat it.
            //
            // That ordering is worth stating, because it is not obvious and it is not local. The
            // collector observing this channel runs on Dispatchers.Main.immediate, so it resumes
            // inline inside trySend and AuthScreen's handler begins running while this job is still
            // active. What saves it is that requestCipher suspends until the user answers, and its
            // one synchronous return is a failure that never reaches executeCreateAccess. A fast
            // path added there that returned a cipher without suspending would be dropped by the
            // guard, and the user would sit on the migrate screen with no account.
            biometricChannel.trySend(BiometricRequest.CreateAccess(password))
            return
        }

        createAllAccesses(
            password = password,
            biometricCipher = null,
        ).handleAuthenticationResult()
    }

    private fun requestBiometricLogin() {
        biometricChannel.trySend(BiometricRequest.Login)
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