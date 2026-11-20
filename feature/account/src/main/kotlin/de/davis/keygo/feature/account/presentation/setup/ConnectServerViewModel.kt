package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.domain.model.ServerAddressError
import de.davis.keygo.feature.account.presentation.setup.mapper.toFailureReason
import de.davis.keygo.feature.account.presentation.setup.mapper.toFound
import de.davis.keygo.feature.account.presentation.setup.mapper.toServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.ChooseUsernameState
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiState
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
import de.davis.keygo.rust.username.UsernameValidator
import de.davis.keygo.rust.username.validateWithResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ConnectServerViewModel(
    private val syncRepository: SyncRepository,
    private val usernameValidator: UsernameValidator,
) : ViewModel() {

    private val addressTextFieldState = TextFieldState()
    private val usernameTextFieldState = TextFieldState()
    private val inviteTextFieldState = TextFieldState()

    private val _detectedScheme = MutableStateFlow<String?>(null)

    private fun address(text: String, scheme: String?) =
        ServerAddress.parse(if (scheme == null || "://" in text) text else scheme + text)

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val typed = combine(
        snapshotFlow { addressTextFieldState.text.toString() },
        _detectedScheme,
        ::address,
    ).distinctUntilChanged().map { CheckRequest(it, afterPause = true) }

    private val retried = retries.map {
        CheckRequest(
            address = address(addressTextFieldState.text.toString(), _detectedScheme.value),
            afterPause = false,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _check = merge(typed, retried).transformLatest { (parsed, afterPause) ->
        if (afterPause) {
            emit(ServerCheck.Idle)
            delay(TYPING_PAUSE)
        }
        parsed.onSuccess { emit(ServerCheck.Checking) }
        emit(parsed.fold(onSuccess = { check(it) }, onFailure = ServerAddressError::toServerCheck))
    }.onStart { emit(ServerCheck.Idle) }

    private suspend fun check(address: ServerAddress): ServerCheck =
        syncRepository.checkServer(address.origin).fold(
            onSuccess = { it.toFound(address) },
            onFailure = { ServerCheck.Failed(it.toFailureReason()) },
        )

    private val allowedUsernameChars = usernameValidator.allowedChars()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _usernameCheck = snapshotFlow { usernameTextFieldState.text.toString() }
        .distinctUntilChanged()
        .transformLatest { input ->
            if (input.isEmpty()) return@transformLatest emit(UsernameCheck.Idle)

            when (val result = usernameValidator.validateWithResult(input)) {
                is Result.Success -> emit(UsernameCheck.Valid(result.success))
                is Result.Failure -> {
                    emit(UsernameCheck.Idle)
                    delay(TYPING_PAUSE)
                    emit(UsernameCheck.Invalid(result.error))
                }
            }
        }.onStart { emit(UsernameCheck.Idle) }

    private val _step = MutableStateFlow(ConnectServerStep.EnterDomain)

    val state = combine(
        _check,
        _detectedScheme,
        _usernameCheck,
        _step,
        ::uiState
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = uiState(
            check = ServerCheck.Idle,
            scheme = null,
            usernameCheck = UsernameCheck.Idle,
            step = ConnectServerStep.EnterDomain,
        ),
    )

    private fun uiState(
        check: ServerCheck,
        scheme: String?,
        usernameCheck: UsernameCheck,
        step: ConnectServerStep,
    ) = ConnectServerUiState(
        enterDomainState = EnterDomainState(
            addressTextFieldState = addressTextFieldState,
            typedScheme = scheme,
            check = check,
        ),
        chooseUsernameState = ChooseUsernameState(
            usernameTextFieldState = usernameTextFieldState,
            inviteTextFieldState = inviteTextFieldState,
            serverHost = addressTextFieldState.text.toString(),
            serverInviteRequired = true,
            inviteEditable = true,
            allowedUsernameChars = allowedUsernameChars,
            usernameCheck = usernameCheck,
        ),
        step = step,
    )

    private val _event = Channel<ConnectServerEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun onEvent(event: ConnectServerUiEvent) {
        when (event) {
            ConnectServerUiEvent.Back,
            ConnectServerUiEvent.UseAnotherServer -> previousStep()

            // The keyboard action bypasses the disabled button.
            ConnectServerUiEvent.Continue -> if (state.value.canContinue) nextStep()

            ConnectServerUiEvent.Retry -> retries.tryEmit(Unit)

            is ConnectServerUiEvent.SchemeChanged -> _detectedScheme.update { event.scheme }

            ConnectServerUiEvent.ScanInvite -> _event.trySend(ConnectServerEvent.OpenInviteScanner)
        }
    }

    private fun nextStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal + 1) ?: step
    }

    private fun previousStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal - 1) ?: step
    }

    private data class CheckRequest(
        val address: Result<ServerAddress, ServerAddressError>,
        val afterPause: Boolean,
    )

    private companion object {
        val TYPING_PAUSE = 500.milliseconds
    }
}
