package de.davis.keygo.item.presentation.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

class PasswordViewModel(
    private val estimateStrength: EstimatePasswordStrengthUseCase
) : ViewModel() {

    private val passwordTextFieldState = TextFieldState()
    private val _uiState = PasswordUiState(passwordTextFieldState = passwordTextFieldState)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val strengthScore = snapshotFlow { passwordTextFieldState.text }
        .debounce(150.milliseconds)
        .mapLatest { estimateStrength(it.toString()) }
        .distinctUntilChanged()

    val state = combine(strengthScore) { (strengthScore) ->
        _uiState.copy(strengthScore = strengthScore)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PasswordUiState()
    )

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnGeneratePasswordClick -> {
                // TODO Handle the event
            }
        }
    }
}