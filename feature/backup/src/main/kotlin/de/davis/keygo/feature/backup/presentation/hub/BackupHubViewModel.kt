package de.davis.keygo.feature.backup.presentation.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.feature.backup.domain.usecase.CancelBackupUseCase
import de.davis.keygo.feature.backup.domain.usecase.ObserveDispatchedBackupsUseCase
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class BackupHubViewModel(
    observeDispatchedBackups: ObserveDispatchedBackupsUseCase,
    observeLastBackup: ObserveLastBackupUseCase,
    private val cancelBackup: CancelBackupUseCase,
) : ViewModel() {

    private val _event = Channel<BackupHubEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    val state: StateFlow<BackupHubUiState> =
        combine(observeDispatchedBackups(), observeLastBackup()) { items, lastBackup ->
            BackupHubUiState(lastBackup = lastBackup, groups = items.toGroups())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BackupHubUiState(),
        )

    fun onEvent(event: BackupHubUiEvent) {
        when (event) {
            BackupHubUiEvent.OnScheduleBackupClick ->
                _event.trySend(BackupHubEvent.NavigateToExport)

            BackupHubUiEvent.OnRestoreBackup -> _event.trySend(BackupHubEvent.NavigateToImport)

            is BackupHubUiEvent.OnCancelBackup ->
                viewModelScope.launch { cancelBackup(event.id, event.kind) }
        }
    }
}
