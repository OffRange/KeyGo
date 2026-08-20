package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.feature.item.create.domain.PasswordGenerator
import de.davis.keygo.feature.item.create.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.feature.item.create.presentation.password.model.GeneratePasswordUiState
import de.davis.keygo.feature.item.create.presentation.password.model.UiCharacterSet
import de.davis.keygo.feature.item.create.presentation.password.model.UiPassword.Companion.asUiPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
internal class GeneratePasswordViewModel(
    private val passwordGenerator: PasswordGenerator,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
) : ViewModel() {

    @OptIn(ExperimentalMaterial3Api::class)
    val sliderState = SliderState(value = 10f, valueRange = 8f..100f)

    private val finalPasswordChannel = Channel<String>()
    val finalPassword = finalPasswordChannel.receiveAsFlow()

    private val _characterSetFlow = MutableStateFlow(UiCharacterSet.ALL)
    private val _manualGenerationTrigger = MutableStateFlow(0)

    val generationState = combine(
        snapshotFlow { sliderState.value.toInt() }
            .debounce(150.milliseconds)
            .distinctUntilChanged(),
        _characterSetFlow,
        _manualGenerationTrigger,
    ) { length, characterSet, _ ->
        val newPassword = passwordGenerator.generatePassword(
            length = length,
            useLowercase = characterSet.selected(UiCharacterSet.LOWERCASE),
            useUppercase = characterSet.selected(UiCharacterSet.UPPERCASE),
            useNumbers = characterSet.selected(UiCharacterSet.DIGITS),
            useSymbols = characterSet.selected(UiCharacterSet.PUNCTUATIONS),
        )
        val score = passwordStrengthEstimator(newPassword)

        GeneratePasswordUiState(
            generatedPassword = newPassword.asUiPassword(),
            passwordStrength = score,
            characterSet = characterSet,
            showCaution = characterSet != UiCharacterSet.ALL,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneratePasswordUiState(),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    fun onEvent(event: GeneratePasswordUiEvent) {
        when (event) {
            is GeneratePasswordUiEvent.OnCharacterSetClick -> {
                _characterSetFlow.update { currentSet ->
                    val newCharacterSet = currentSet.toggle(event.uiCharacterSet)
                    // Only update if it's valid, otherwise keep the old one
                    if (newCharacterSet != UiCharacterSet.NONE) newCharacterSet else currentSet
                }
            }

            is GeneratePasswordUiEvent.OnGeneratePasswordClick -> _manualGenerationTrigger.update { it + 1 }

            is GeneratePasswordUiEvent.OnUseClick -> {
                viewModelScope.launch {
                    finalPasswordChannel.send(generationState.value.generatedPassword.value)
                }
            }
        }
    }
}