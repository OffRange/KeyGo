package de.davis.keygo.feature.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.repository.ServerLinkRepository
import de.davis.keygo.feature.account.presentation.mapper.toAccountState
import de.davis.keygo.feature.account.presentation.models.AccountEvent
import de.davis.keygo.feature.account.presentation.models.AccountState
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AccountViewModel(serverLinkRepository: ServerLinkRepository) : ViewModel() {

    val uiState = serverLinkRepository.observeLink()
        .map { it.toAccountState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountState.Local
        )

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