package de.davis.keygo.item.presentation.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.navigation.Navigator
import de.davis.keygo.item.domain.PasswordGenerator
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class PasswordViewModel(
    passwordGenerator: PasswordGenerator,
    private val navigator: Navigator,
    private val estimateStrength: EstimatePasswordStrengthUseCase
) : GeneratePasswordViewModel(passwordGenerator, estimateStrength) {

    private val passwordTextFieldState = TextFieldState()
    private val _uiState =
        MutableStateFlow(PasswordUiState(passwordTextFieldState = passwordTextFieldState))

    val state = _uiState
        .onStart {
            observePasswordTextField()
            observeGenerator()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PasswordUiState(passwordTextFieldState = passwordTextFieldState)
        )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordTextField() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { estimateStrength(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                _uiState.update {
                    it.copy(strengthScore = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun observeGenerator() {
        generationState.onEach { state ->
            _uiState.update {
                it.copy(generatePasswordState = state)
            }
        }.launchIn(viewModelScope)

        finalPassword.onEach { password ->
            passwordTextFieldState.edit {
                replace(0, length, password)
            }
            _uiState.update {
                it.copy(generatePasswordBottomSheetVisible = false)
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnGeneratePasswordClick -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is PasswordUiEvent.OnBackClick -> {
                viewModelScope.launch {
                    navigator.navigateUp(detail = true)
                }
            }

            is PasswordUiEvent.OnCloseBottomSheet -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is GeneratePasswordUiEvent -> super.onEvent(event)
        }
    }
}