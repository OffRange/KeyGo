package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiState
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ConnectServerViewModel : ViewModel() {

    private val addressTextFieldState = TextFieldState()

    private val _check = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    private val _step = MutableStateFlow(ConnectServerStep.EnterDomain)

    val state = combine(_check, _step) { check, step ->
        ConnectServerUiState(
            enterDomainState = EnterDomainState(
                addressTextFieldState = addressTextFieldState,
                check = check,
            ),
            step = step,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectServerUiState(
            enterDomainState = EnterDomainState(addressTextFieldState = addressTextFieldState),
        ),
    )

    private val _event = Channel<ConnectServerEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun onEvent(event: ConnectServerUiEvent) {
        when (event) {
            ConnectServerUiEvent.Back,
            ConnectServerUiEvent.UseAnotherServer -> previousStep()

            ConnectServerUiEvent.Continue -> nextStep()

            ConnectServerUiEvent.ScanInvite -> _event.trySend(ConnectServerEvent.OpenInviteScanner)
        }
    }

    private fun nextStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal + 1) ?: step
    }

    private fun previousStep() = _step.update { step ->
        ConnectServerStep.entries.getOrNull(step.ordinal - 1) ?: step
    }
}
