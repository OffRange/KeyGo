package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.VerifyPasswordError
import de.davis.keygo.core.identity.domain.usecase.VerifyPasswordUseCase
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.combine
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.presentation.setup.mapper.toFailureReason
import de.davis.keygo.feature.account.presentation.setup.mapper.toFound
import de.davis.keygo.feature.account.presentation.setup.mapper.toServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.ChooseUsernameState
import de.davis.keygo.feature.account.presentation.setup.model.ConfirmPasswordState
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiState
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
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
import kotlinx.coroutines.flow.onStart
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
    private val verifyPassword: VerifyPasswordUseCase,
    private val session: Session,
) : ViewModel() {

    private val addressTextFieldState = TextFieldState()
    private val usernameTextFieldState = TextFieldState()
    private val inviteTextFieldState = TextFieldState()
    private val passwordTextFieldState = TextFieldState()

    private val _detectedScheme = MutableStateFlow<String?>(null)

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _step = MutableStateFlow(ConnectServerStep.EnterDomain)

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
    }.onStart { emit(ServerCheck.Idle) }

    // --- Username - Invite ---
    @OptIn(FlowPreview::class)
    private val _usernameCheck = snapshotFlow { usernameTextFieldState.text.toString() }
        .debounce(TYPING_PAUSE)
        .map(::usernameCheck)
        .onStart { emit(UsernameCheck.Idle) }

    private fun usernameCheck(input: String): UsernameCheck =
        if (input.isEmpty()) UsernameCheck.Idle
        else usernameValidator.validateWithResult(input).fold(
            onSuccess = UsernameCheck::Valid,
            onFailure = UsernameCheck::Invalid,
        )

    // --- Password ---
    private val _passwordCheck = MutableStateFlow<PasswordCheck>(PasswordCheck.Idle)

    private fun fields() = Fields(
        address = addressTextFieldState.text.toString(),
        username = usernameTextFieldState.text.toString(),
        invite = inviteTextFieldState.text.toString(),
        passwordEntered = passwordTextFieldState.text.isNotEmpty(),
    )

    private val _fields = snapshotFlow(::fields)

    val state = combine(
        _check,
        _detectedScheme,
        _usernameCheck,
        _passwordCheck,
        _step,
        _fields,
        ::uiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = uiState(
            check = ServerCheck.Idle,
            scheme = null,
            usernameCheck = UsernameCheck.Idle,
            passwordCheck = PasswordCheck.Idle,
            step = ConnectServerStep.EnterDomain,
            fields = fields(),
        ),
    )

    private fun uiState(
        check: ServerCheck,
        scheme: String?,
        usernameCheck: UsernameCheck,
        passwordCheck: PasswordCheck,
        step: ConnectServerStep,
        fields: Fields,
    ) = ConnectServerUiState(
        enterDomainState = EnterDomainState(
            addressTextFieldState = addressTextFieldState,
            typedScheme = scheme,
            check = check,
        ),
        chooseUsernameState = ChooseUsernameState(
            usernameTextFieldState = usernameTextFieldState,
            inviteTextFieldState = inviteTextFieldState,
            serverHost = fields.address,
            serverInviteRequired = check.inviteRequired,
            inviteEditable = true,
            allowedUsernameChars = allowedUsernameChars,
            usernameCheck = usernameCheck,
        ),
        confirmPasswordState = ConfirmPasswordState(
            passwordTextFieldState = passwordTextFieldState,
            check = passwordCheck,
        ),
        step = step,
        canContinue = canContinue(step, check, passwordCheck, scheme, fields),
    )

    private fun canContinue(
        step: ConnectServerStep,
        check: ServerCheck,
        passwordCheck: PasswordCheck,
        scheme: String?,
        fields: Fields,
    ): Boolean = when (step) {
        ConnectServerStep.EnterDomain ->
            check is ServerCheck.Found &&
                    check.serverUrl == address(fields.address, scheme).getOrNull()?.origin

        ConnectServerStep.ChooseUsername ->
            usernameCheck(fields.username) is UsernameCheck.Valid &&
                    (!check.inviteRequired || fields.invite.isNotBlank())

        ConnectServerStep.ConfirmPassword ->
            fields.passwordEntered && passwordCheck != PasswordCheck.Checking
    }

    private val ServerCheck.inviteRequired: Boolean
        get() = this is ServerCheck.Found && inviteOnly

    private val _event = Channel<ConnectServerEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun onEvent(event: ConnectServerUiEvent) {
        when (event) {
            ConnectServerUiEvent.Back,
            ConnectServerUiEvent.UseAnotherServer -> previousStep()

            ConnectServerUiEvent.Continue -> if (
                canContinue(
                    step = _step.value,
                    check = state.value.enterDomainState.check,
                    passwordCheck = _passwordCheck.value,
                    scheme = _detectedScheme.value,
                    fields = fields(),
                )
            ) continueStep()

            ConnectServerUiEvent.Retry -> retries.tryEmit(Unit)

            is ConnectServerUiEvent.SchemeChanged -> _detectedScheme.update { event.scheme }

            ConnectServerUiEvent.ScanInvite -> _event.trySend(ConnectServerEvent.OpenInviteScanner)
        }
    }

    init {
        viewModelScope.launch {
            session.isActive.filter { !it }.collect { clearPassword() }
        }
    }

    private fun continueStep() =
        if (_step.value == ConnectServerStep.ConfirmPassword) confirmPassword()
        else nextStep()

    private fun confirmPassword() {
        _passwordCheck.update { PasswordCheck.Checking }
        viewModelScope.launch {
            verifyPassword(passwordTextFieldState.text.toString())
                .onSuccess {
                    _passwordCheck.update { PasswordCheck.Idle }
                    nextStep()
                }
                .onFailure {
                    _passwordCheck.update { _ ->
                        if (it == VerifyPasswordError.IncorrectPassword) PasswordCheck.Incorrect
                        else PasswordCheck.Failed
                    }
                }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun clearPassword() {
        passwordTextFieldState.clearText()
        passwordTextFieldState.undoState.clearHistory()
        _passwordCheck.update { PasswordCheck.Idle }
    }

    private fun nextStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal + 1) ?: step
    }

    private fun previousStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal - 1) ?: step
    }

    private suspend fun check(address: ServerAddress): ServerCheck =
        syncRepository.checkServer(address.origin).fold(
            onSuccess = { it.toFound(address) },
            onFailure = { ServerCheck.Failed(it.toFailureReason()) },
        )

    private data class Fields(
        val address: String,
        val username: String,
        val invite: String,
        val passwordEntered: Boolean,
    )

    private companion object {
        val TYPING_PAUSE = 500.milliseconds
    }
}
