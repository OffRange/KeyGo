package de.davis.keygo.item.create.presentation.password

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.create.domain.PasswordGenerator
import de.davis.keygo.item.create.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.GeneratePasswordUiState
import de.davis.keygo.item.create.presentation.password.model.UiCharacterSet
import de.davis.keygo.item.create.presentation.password.model.UiPassword.Companion.asUiPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
open class GeneratePasswordViewModel(
    private val passwordGenerator: PasswordGenerator,
    private val estimatePasswordStrength: EstimatePasswordStrengthUseCase,
) : ViewModel() {

    @OptIn(ExperimentalMaterial3Api::class)
    private val sliderState = SliderState(value = 10f, valueRange = 8f..100f)

    private val finalPasswordChannel = Channel<String>()
    val finalPassword = finalPasswordChannel.receiveAsFlow()

    @OptIn(ExperimentalMaterial3Api::class)
    private val _generationState =
        MutableStateFlow(GeneratePasswordUiState(sliderState = sliderState))
    val generationState = _generationState
        .onStart {
            observeLength()
            observeCharacterSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneratePasswordUiState(sliderState = sliderState)
        )

    private fun observeLength() {
        snapshotFlow { sliderState.value.toInt() }
            .debounce(150.milliseconds)
            .distinctUntilChanged()
            .onEach { newLength ->
                generateAndUpdatePassword(length = newLength)
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun observeCharacterSet() {
        generationState
            .distinctUntilChangedBy { it.characterSet }
            .map { it.characterSet }
            .onEach { characterSet ->
                generateAndUpdatePassword(characterSet = characterSet)
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private suspend fun generateAndUpdatePassword(
        length: Int = sliderState.value.toInt(),
        characterSet: UiCharacterSet = _generationState.value.characterSet,
    ) {
        val newPassword = passwordGenerator.generatePassword(
            length = length,
            useLowercase = characterSet.selected(UiCharacterSet.LOWERCASE),
            useUppercase = characterSet.selected(UiCharacterSet.UPPERCASE),
            useNumbers = characterSet.selected(UiCharacterSet.DIGITS),
            useSymbols = characterSet.selected(UiCharacterSet.PUNCTUATIONS),
        )
        val score = estimatePasswordStrength(newPassword)
        _generationState.update { ui ->
            ui.copy(generatedPassword = newPassword.asUiPassword(), passwordStrength = score)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun onEvent(event: GeneratePasswordUiEvent) {
        when (event) {
            is GeneratePasswordUiEvent.OnCharacterSetClick -> {
                _generationState.update {
                    val newCharacterSet = it.characterSet.toggle(event.uiCharacterSet)
                    if (newCharacterSet == UiCharacterSet.NONE) return

                    it.copy(
                        characterSet = newCharacterSet,
                        showCaution = newCharacterSet != UiCharacterSet.ALL,
                    )
                }
            }

            is GeneratePasswordUiEvent.OnGeneratePasswordClick -> {
                viewModelScope.launch {
                    generateAndUpdatePassword()
                }
            }

            is GeneratePasswordUiEvent.OnUseClick -> {
                viewModelScope.launch {
                    finalPasswordChannel.send(generationState.value.generatedPassword.value)
                }
            }
        }
    }
}