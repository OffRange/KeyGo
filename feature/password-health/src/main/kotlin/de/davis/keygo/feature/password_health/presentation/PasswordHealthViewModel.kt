package de.davis.keygo.feature.password_health.presentation

import androidx.lifecycle.ViewModel
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PasswordHealthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordHealthUiState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: PasswordHealthUiEvent) {

    }
}