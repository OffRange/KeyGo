package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.sync.domain.model.RegisterError
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.sync.domain.usecase.RegisterUseCase
import de.davis.keygo.core.ui.text.clearSecret
import de.davis.keygo.core.util.combine
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.presentation.setup.mapper.toFound
import de.davis.keygo.feature.account.presentation.setup.mapper.toPasswordCheck
import de.davis.keygo.feature.account.presentation.setup.mapper.toServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.AccountChoice
import de.davis.keygo.feature.account.presentation.setup.model.ChooseUsernameState
import de.davis.keygo.feature.account.presentation.setup.model.ConfirmPasswordState
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiState
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.SignInCheck
import de.davis.keygo.feature.account.presentation.setup.model.SignInOrOutState
import de.davis.keygo.feature.account.presentation.setup.model.SignInState
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
import de.davis.keygo.feature.account.presentation.setup.model.stepsFor
import de.davis.keygo.rust.username.UsernameValidator
import de.davis.keygo.rust.username.validateWithResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ConnectServerViewModel(
    private val syncRepository: SyncRepository,
    private val usernameValidator: UsernameValidator,
    private val register: RegisterUseCase,
    private val session: Session,
) : ViewModel() {

    private val addressTextFieldState = TextFieldState()
    private val usernameTextFieldState = TextFieldState()
    private val inviteTextFieldState = TextFieldState()
    private val passwordTextFieldState = TextFieldState()
    private val signInUsernameTextFieldState = TextFieldState()
    private val signInPasswordTextFieldState = TextFieldState()

    private val _detectedScheme = MutableStateFlow<String?>(null)

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _route = MutableStateFlow(Route())

    private fun address(text: String, scheme: String?) =
        ServerAddress.parse(if (scheme == null || "://" in text) text else scheme + text)

    private fun currentAddress() =
        address(addressTextFieldState.text.toString(), _detectedScheme.value)

    private val allowedUsernameChars = usernameValidator.allowedChars()

    @OptIn(FlowPreview::class)
    private val typed = combine(
        snapshotFlow { addressTextFieldState.text.toString() },
        _detectedScheme,
        ::address,
    ).debounce(TYPING_PAUSE).distinctUntilChanged()

    // --- Server address ---
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _check = merge(
        typed,
        retries.map { currentAddress() }, // A retry skips the pause.
    ).transformLatest { parsed ->
        parsed.fold(
            onSuccess = { address ->
                emit(ServerCheck.Checking)
                emit(check(address))
            },
            onFailure = { emit(it.toServerCheck()) },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ServerCheck.Idle)

    // --- Username - Invite ---
    @OptIn(FlowPreview::class)
    private val _usernameCheck = snapshotFlow { usernameTextFieldState.text.toString() }
        .debounce(TYPING_PAUSE)
        .map(::usernameCheck)
        .stateIn(viewModelScope, SharingStarted.Lazily, UsernameCheck.Idle)

    private fun usernameCheck(input: String): UsernameCheck =
        if (input.isEmpty()) UsernameCheck.Idle
        else usernameValidator.validateWithResult(input).fold(
            onSuccess = UsernameCheck::Valid,
            onFailure = UsernameCheck::Invalid,
        )

    // --- Password ---
    private val _passwordCheck = MutableStateFlow<PasswordCheck>(PasswordCheck.Idle)

    // --- Sign in ---
    private val _signInCheck = MutableStateFlow<SignInCheck>(SignInCheck.Idle)

    // --- Rejected by the server on register ---
    private val _rejection = MutableStateFlow<Rejection?>(null)
    private val _inviteRequiredBy = MutableStateFlow<String?>(null)

    private fun fields() = Fields(
        address = addressTextFieldState.text.toString(),
        username = usernameTextFieldState.text.toString(),
        invite = inviteTextFieldState.text.toString(),
        passwordEntered = passwordTextFieldState.text.isNotEmpty(),
        signInUsernameEntered = signInUsernameTextFieldState.text.isNotBlank(),
        signInPasswordEntered = signInPasswordTextFieldState.text.isNotEmpty(),
    )

    private val _fields = snapshotFlow(::fields)

    val state = combine(
        _check,
        _detectedScheme,
        _usernameCheck,
        _passwordCheck,
        _signInCheck,
        _route,
        _fields,
        _rejection,
        _inviteRequiredBy,
        ::uiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = uiState(
            check = ServerCheck.Idle,
            scheme = null,
            usernameCheck = UsernameCheck.Idle,
            passwordCheck = PasswordCheck.Idle,
            signInCheck = SignInCheck.Idle,
            route = Route(),
            fields = fields(),
            rejection = null,
            inviteRequiredBy = null,
        ),
    )

    private fun uiState(
        check: ServerCheck,
        scheme: String?,
        usernameCheck: UsernameCheck,
        passwordCheck: PasswordCheck,
        signInCheck: SignInCheck,
        route: Route,
        fields: Fields,
        rejection: Rejection?,
        inviteRequiredBy: String?,
    ) = ConnectServerUiState(
        enterDomainState = EnterDomainState(
            addressTextFieldState = addressTextFieldState,
            typedScheme = scheme,
            check = check,
        ),
        signInOrOutState = SignInOrOutState(
            serverHost = fields.address,
            serverVersion = (check as? ServerCheck.Found)?.version,
            signupsOpen = !inviteRequired(check, inviteRequiredBy),
        ),
        chooseUsernameState = ChooseUsernameState(
            usernameTextFieldState = usernameTextFieldState,
            inviteTextFieldState = inviteTextFieldState,
            serverHost = fields.address,
            serverInviteRequired = inviteRequired(check, inviteRequiredBy),
            inviteEditable = true,
            allowedUsernameChars = allowedUsernameChars,
            usernameCheck = rejection.usernameCheck(fields.username) ?: usernameCheck,
            inviteError = inviteError(check, fields.invite, rejection, inviteRequiredBy),
        ),
        confirmPasswordState = ConfirmPasswordState(
            passwordTextFieldState = passwordTextFieldState,
            check = passwordCheck,
        ),
        signInState = SignInState(
            usernameTextFieldState = signInUsernameTextFieldState,
            passwordTextFieldState = signInPasswordTextFieldState,
            serverHost = fields.address,
            check = signInCheck,
        ),
        step = route.step,
        steps = route.steps,
        canContinue = canContinue(
            route.step,
            check,
            passwordCheck,
            signInCheck,
            scheme,
            fields,
            rejection,
            inviteRequiredBy,
        ),
    )

    private fun canContinue(
        step: ConnectServerStep,
        check: ServerCheck,
        passwordCheck: PasswordCheck,
        signInCheck: SignInCheck,
        scheme: String?,
        fields: Fields,
        rejection: Rejection?,
        inviteRequiredBy: String?,
    ): Boolean = when (step) {
        ConnectServerStep.EnterDomain -> check is ServerCheck.Found &&
                check.serverUrl == address(fields.address, scheme).getOrNull()?.origin

        // The step has no continue button: picking an option moves on.
        ConnectServerStep.SignInOrOut -> false

        ConnectServerStep.ChooseUsername ->
            (rejection.usernameCheck(fields.username)
                ?: usernameCheck(fields.username)) is UsernameCheck.Valid &&
                    (!inviteRequired(check, inviteRequiredBy) || fields.invite.isNotBlank()) &&
                    inviteError(check, fields.invite, rejection, inviteRequiredBy) == null

        ConnectServerStep.ConfirmPassword -> fields.passwordEntered && passwordCheck != PasswordCheck.Checking

        ConnectServerStep.SignIn -> fields.signInUsernameEntered && fields.signInPasswordEntered &&
                signInCheck != SignInCheck.Checking
    }

    private val ServerCheck.inviteRequired: Boolean
        get() = this is ServerCheck.Found && inviteOnly

    private fun ServerCheck.isFoundFor(serverUrl: String?) =
        this is ServerCheck.Found && this@isFoundFor.serverUrl == serverUrl

    private fun inviteRequired(check: ServerCheck, inviteRequiredBy: String?) =
        check.inviteRequired || check.isFoundFor(inviteRequiredBy)

    private fun inviteError(
        check: ServerCheck,
        invite: String,
        rejection: Rejection?,
        inviteRequiredBy: String?,
    ): SyncError? = when {
        rejection is Rejection.Invite && rejection.invite == invite -> SyncError.InviteInvalid
        invite.isBlank() && check.isFoundFor(inviteRequiredBy) -> SyncError.InviteRequired

        else -> null
    }

    private fun Rejection?.usernameCheck(username: String): UsernameCheck? =
        (this as? Rejection.Username)?.takeIf { it.username == username }
            ?.let { UsernameCheck.Rejected(it.error) }

    private val _event = Channel<ConnectServerEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun onEvent(event: ConnectServerUiEvent) {
        when (event) {
            ConnectServerUiEvent.Back,
            ConnectServerUiEvent.UseAnotherServer -> if (!requestInFlight()) previousStep()

            ConnectServerUiEvent.Continue -> if (
                canContinue(
                    step = _route.value.step,
                    check = _check.value,
                    passwordCheck = _passwordCheck.value,
                    signInCheck = _signInCheck.value,
                    scheme = _detectedScheme.value,
                    fields = fields(),
                    rejection = _rejection.value,
                    inviteRequiredBy = _inviteRequiredBy.value,
                )
            ) continueStep()

            ConnectServerUiEvent.Retry -> retries.tryEmit(Unit)

            is ConnectServerUiEvent.ChooseAccount -> chooseAccount(event.choice)

            is ConnectServerUiEvent.SchemeChanged -> _detectedScheme.update { event.scheme }

            ConnectServerUiEvent.ScanInvite -> _event.trySend(ConnectServerEvent.OpenInviteScanner)
        }
    }

    init {
        viewModelScope.launch {
            session.isActive.filter { !it }.collect { clearPassword() }
        }
    }

    private fun continueStep() = when (_route.value.step) {
        ConnectServerStep.ConfirmPassword -> confirmPassword()
        ConnectServerStep.SignIn -> signIn()
        else -> nextStep()
    }

    private fun confirmPassword() {
        val server = _check.value as? ServerCheck.Found ?: return
        val username = usernameTextFieldState.text.toString()
        val invite = inviteTextFieldState.text.toString()

        _passwordCheck.update { PasswordCheck.Checking }
        viewModelScope.launch {
            register(
                serverUrl = server.serverUrl,
                username = username,
                password = passwordTextFieldState.text.toString(),
                invite = invite.takeIf { it.isNotBlank() },
            )
                .onSuccess {
                    clearPassword()
                    _event.send(ConnectServerEvent.AccountCreated)
                }
                .onFailure { onRegisterFailed(it, server.serverUrl, username, invite) }
        }
    }

    private fun signIn() {
        val server = _check.value as? ServerCheck.Found ?: return

        _signInCheck.update { SignInCheck.Checking }
        viewModelScope.launch {
            syncRepository.login(
                serverUrl = server.serverUrl,
                username = signInUsernameTextFieldState.text.toString(),
                password = signInPasswordTextFieldState.text.toString(),
            ).onSuccess {
                clearPassword()
                _event.send(ConnectServerEvent.SignedIn)
            }.onFailure { error -> _signInCheck.update { SignInCheck.Failed(error) } }
        }
    }

    private fun onRegisterFailed(
        error: RegisterError,
        serverUrl: String,
        username: String,
        invite: String,
    ) = when (val syncError = (error as? RegisterError.Sync)?.error) {
        SyncError.UsernameTaken,
        SyncError.InvalidUsername -> backToUsername(Rejection.Username(username, syncError))

        SyncError.InviteInvalid -> backToUsername(Rejection.Invite(invite))

        SyncError.InviteRequired -> {
            _inviteRequiredBy.update { serverUrl }
            backToUsername(null)
        }

        else -> _passwordCheck.update { error.toPasswordCheck() }
    }

    private fun backToUsername(rejection: Rejection?) {
        _rejection.update { rejection }
        _passwordCheck.update { PasswordCheck.Idle }
        _route.update { it.copy(step = ConnectServerStep.ChooseUsername) }
    }

    private fun clearPassword() {
        passwordTextFieldState.clearSecret()
        signInPasswordTextFieldState.clearSecret()
        _passwordCheck.update { PasswordCheck.Idle }
        _signInCheck.update { SignInCheck.Idle }
    }

    private fun chooseAccount(choice: AccountChoice) {
        if (_route.value.step != ConnectServerStep.SignInOrOut) return

        _route.update { it.copy(choice = choice) }
        nextStep()
    }

    /**
     * A register or login is running. Its result moves the wizard (back to the username, or off
     * the screen), so the user must not leave the step, or switch paths, until it lands.
     */
    private fun requestInFlight() =
        _passwordCheck.value == PasswordCheck.Checking || _signInCheck.value == SignInCheck.Checking

    private fun nextStep() = _route.update { it.moveBy(1) }

    /**
     * Going back keeps what the user typed, except a password: nothing before its step reads it.
     * Back on the address step the server may change, so what the last server said about the
     * username or invite no longer applies.
     *
     * Back is blocked while a request runs, so resetting the password checks never hides one.
     */
    private fun previousStep() {
        val leaving = _route.value.step
        _route.update { it.moveBy(-1) }
        if (leaving == ConnectServerStep.ConfirmPassword || leaving == ConnectServerStep.SignIn)
            clearPassword()
        if (_route.value.step == ConnectServerStep.EnterDomain) _rejection.update { null }
    }

    private suspend fun check(address: ServerAddress): ServerCheck =
        syncRepository.checkServer(address.origin).fold(
            onSuccess = { it.toFound(address) },
            onFailure = { ServerCheck.Failed(it) },
        )

    private sealed interface Rejection {
        data class Username(val username: String, val error: SyncError) : Rejection
        data class Invite(val invite: String) : Rejection
    }

    /**
     * Where the wizard stands. The [choice] made on [ConnectServerStep.SignInOrOut] picks the path;
     * it is kept when the user steps back, so the step indicator does not jump while they do.
     */
    private data class Route(
        val step: ConnectServerStep = ConnectServerStep.EnterDomain,
        val choice: AccountChoice? = null,
    ) {
        val steps: List<ConnectServerStep> = stepsFor(choice)

        fun moveBy(offset: Int): Route =
            copy(step = steps.getOrNull(steps.indexOf(step) + offset) ?: step)
    }

    private data class Fields(
        val address: String,
        val username: String,
        val invite: String,
        val passwordEntered: Boolean,
        val signInUsernameEntered: Boolean,
        val signInPasswordEntered: Boolean,
    )

    private companion object {
        val TYPING_PAUSE = 500.milliseconds
    }
}
