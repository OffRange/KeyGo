package de.davis.keygo.feature.account.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.core.sync.domain.repository.ServerLinkRepository
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.ui.text.clearSecret
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.account.presentation.models.AccountEvent
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent
import de.davis.keygo.feature.account.presentation.models.AccountUiState
import de.davis.keygo.feature.account.presentation.models.DeleteAccountDialogState
import de.davis.keygo.feature.account.presentation.models.DevicesSummary
import de.davis.keygo.feature.account.presentation.models.LogoutDialogState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AccountViewModel(
    private val syncRepository: SyncRepository,
    private val serverLinkRepository: ServerLinkRepository,
) : ViewModel() {

    private val _logoutDialog = MutableStateFlow(LogoutDialogState.Hidden)
    private val _deleteAccountDialog =
        MutableStateFlow<DeleteAccountDialogState>(DeleteAccountDialogState.Hidden)

    val deleteAccountPassword = TextFieldState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val devices = serverLinkRepository.observeLink()
        .transformLatest { link ->
            emit(DevicesSummary.Loading)
            if (link !is ServerLink.Connected) return@transformLatest

            emit(
                syncRepository.devices().fold(
                    onSuccess = {
                        DevicesSummary.Loaded(
                            total = it.size,
                            signedIn = it.count(Device::loggedIn),
                        )
                    },
                    onFailure = { DevicesSummary.Failed },
                ),
            )
        }

    val uiState = combine(
        serverLinkRepository.observeLink(),
        _logoutDialog,
        devices,
        _deleteAccountDialog,
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

            AccountUiEvent.OnDevicesClicked -> _event.trySend(AccountEvent.NavigateToDevices)

            AccountUiEvent.OnLogoutClicked -> _logoutDialog.update { LogoutDialogState.Confirming }
            AccountUiEvent.OnLogoutConfirmed -> logout()
            AccountUiEvent.OnLogoutDismissed ->
                if (_logoutDialog.value != LogoutDialogState.LoggingOut)
                    _logoutDialog.update { LogoutDialogState.Hidden }

            AccountUiEvent.OnDeleteAccountClicked ->
                _deleteAccountDialog.update { DeleteAccountDialogState.Confirming() }

            AccountUiEvent.OnDeleteAccountConfirmed -> deleteAccount()
            AccountUiEvent.OnDeleteAccountDismissed -> dismissDeleteAccount()

            AccountUiEvent.ResetPassword -> _event.trySend(AccountEvent.NavigateToChangePassword)
        }
    }

    private fun deleteAccount() {
        if (_deleteAccountDialog.value !is DeleteAccountDialogState.Confirming) return
        val password = deleteAccountPassword.text.toString()
        if (password.isEmpty()) return
        deleteAccountPassword.clearSecret()

        _deleteAccountDialog.update { DeleteAccountDialogState.Deleting }
        viewModelScope.launch {
            syncRepository.deleteAccount(password)
                .onSuccess { _deleteAccountDialog.update { DeleteAccountDialogState.Hidden } }
                .onFailure { error ->
                    _deleteAccountDialog.update { DeleteAccountDialogState.Confirming(error) }
                }
        }
    }

    private fun dismissDeleteAccount() {
        if (_deleteAccountDialog.value == DeleteAccountDialogState.Deleting) return

        deleteAccountPassword.clearSecret()
        _deleteAccountDialog.update { DeleteAccountDialogState.Hidden }
    }

    override fun onCleared() {
        deleteAccountPassword.clearSecret()
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
