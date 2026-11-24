package de.davis.keygo.feature.account.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.account.presentation.devices.mapper.toDevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

@KoinViewModel
internal class DevicesViewModel(
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicesState>(DevicesState.Loading)
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: DevicesUiEvent) {
        when (event) {
            DevicesUiEvent.OnRetryClicked -> load()
            DevicesUiEvent.OnResumed -> load(inPlace = _uiState.value is DevicesState.Loaded)
        }
    }

    private fun load(inPlace: Boolean = false) {
        if (loadJob?.isActive == true) return

        if (!inPlace) _uiState.update { DevicesState.Loading }
        loadJob = viewModelScope.launch {
            _uiState.update { state ->
                syncRepository.devices().fold(
                    onSuccess = { it.toDevicesState(now = Clock.System.now()) },
                    onFailure = { if (inPlace) state else DevicesState.Failed },
                )
            }
        }
    }
}
