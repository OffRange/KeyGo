package de.davis.keygo.feature.backup.presentation.hub

import androidx.lifecycle.ViewModel
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class BackupHubViewModel : ViewModel() {

    private val _event = Channel<BackupHubEvent>()
    val event = _event.receiveAsFlow()

    private val _state = MutableStateFlow(BackupHubUiState())
    val state = _state.asStateFlow()

    fun onEvent(event: BackupHubUiEvent) {
        when (event) {
            BackupHubUiEvent.OnRestoreBackup -> {} //TODO
            BackupHubUiEvent.OnScheduleBackupClick -> _event.trySend(BackupHubEvent.NavigateToExport)
        }
    }
}