package de.davis.keygo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
}