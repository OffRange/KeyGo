package de.davis.keygo.feature.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.core.sync.domain.repository.ServerLinkRepository
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.feature.account.presentation.models.AccountEvent
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent
import de.davis.keygo.feature.account.presentation.models.AccountUiState
import de.davis.keygo.feature.account.presentation.models.LogoutDialogState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AccountViewModel(
    private val syncRepository: SyncRepository,
    private val serverLinkRepository: ServerLinkRepository,
) : ViewModel() {

    private val _logoutDialog = MutableStateFlow(LogoutDialogState.Hidden)

    val uiState = combine(
        serverLinkRepository.observeLink(),
        _logoutDialog,
        ::AccountUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(),
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

            AccountUiEvent.OnLogoutClicked -> _logoutDialog.update { LogoutDialogState.Confirming }
            AccountUiEvent.OnLogoutConfirmed -> logout()
            AccountUiEvent.OnLogoutDismissed ->
                if (_logoutDialog.value != LogoutDialogState.LoggingOut)
                    _logoutDialog.update { LogoutDialogState.Hidden }

            AccountUiEvent.ResetPassword -> _event.trySend(AccountEvent.NavigateToChangePassword)
        }
    }

    private fun logout() {
        if (_logoutDialog.value == LogoutDialogState.LoggingOut) return

        _logoutDialog.update { LogoutDialogState.LoggingOut }
        viewModelScope.launch {
            // TODO: maybe fix this:
            // An unreachable server still ends the session locally, so the stored link is the
            // verdict, not the result.
            syncRepository.logout()
            val loggedOut = serverLinkRepository.observeLink().first() == ServerLink.None
            _logoutDialog.update {
                if (loggedOut) LogoutDialogState.Hidden
                else LogoutDialogState.Failed
            }
        }
    }
}
