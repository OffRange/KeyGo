package de.davis.keygo.feature.account.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.account.presentation.devices.mapper.toDevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesEvent
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

@KoinViewModel
internal class DevicesViewModel(
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<DevicesEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var loadJob: Job? = null

    fun onEvent(event: DevicesUiEvent) {
        when (event) {
            DevicesUiEvent.OnRetryClicked -> load(reset = true)
            DevicesUiEvent.OnRefresh -> load(pulled = true)
            // Also the first load: the screen's first resume finds nothing loaded yet.
            // A list already on screen is re-read silently; anything else starts over.
            DevicesUiEvent.OnResumed -> load(reset = _uiState.value.devices !is DevicesState.Loaded)
        }
    }

    private fun load(reset: Boolean = false, pulled: Boolean = false) {
        if (pulled) _uiState.update {
            if (it.devices is DevicesState.Loading) it else it.copy(isRefreshing = true)
        }

        if (loadJob?.isActive == true) return

        if (reset) _uiState.update { it.copy(devices = DevicesState.Loading) }
        loadJob = viewModelScope.launch {
            val result = syncRepository.devices()
            val before = _uiState.value
            _uiState.update { state ->
                state.copy(
                    isRefreshing = false,
                    devices = result.fold(
                        onSuccess = { it.toDevicesState(now = Clock.System.now()) },
                        onFailure = {
                            state.devices as? DevicesState.Loaded ?: DevicesState.Failed
                        },
                    ),
                )
            }

            // isRefreshing is only ever set by a pull, so it marks a user waiting on this load.
            // A failed silent re-read stays silent; a failed pull must not look like a success.
            val pullFailed = result is Result.Failure &&
                    before.isRefreshing &&
                    before.devices is DevicesState.Loaded
            if (pullFailed) _event.send(DevicesEvent.RefreshFailed)
        }
    }
}
