package de.davis.keygo.feature.account.presentation

import androidx.lifecycle.ViewModel
import de.davis.keygo.feature.account.presentation.models.AccountEvent
import de.davis.keygo.feature.account.presentation.models.AccountState
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AccountViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AccountState.Local)
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<AccountEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun onEvent(event: AccountUiEvent) {
        when (event) {
            AccountUiEvent.OnBackClicked -> _event.trySend(AccountEvent.NavigateBack)
            AccountUiEvent.OnConnectClicked -> _event.trySend(AccountEvent.NavigateToConnectServer)

            AccountUiEvent.OnScanInviteClicked -> {
                // TODO
            }

            AccountUiEvent.ResetPassword -> _event.trySend(AccountEvent.NavigateToChangePassword)
        }
    }
}