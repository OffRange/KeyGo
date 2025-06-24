package de.davis.keygo.item.presentation.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.usecase.InsertVaultItem
import de.davis.keygo.core.presentation.model.InputFieldError
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.item.domain.PasswordGenerator
import de.davis.keygo.item.domain.model.PasswordError
import de.davis.keygo.item.domain.usecase.CreateNewPassword
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
class PasswordViewModel(
    passwordGenerator: PasswordGenerator,
    private val estimateStrength: EstimatePasswordStrengthUseCase,
    private val createNewPassword: CreateNewPassword,
    private val insertVaultItem: InsertVaultItem
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

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

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
            passwordTextFieldState.setTextAndPlaceCursorAtEnd(password)
            _uiState.update {
                it.copy(generatePasswordBottomSheetVisible = false)
            }
        }.launchIn(viewModelScope)
    }

    private fun navigateUp() {
        viewModelScope.launch {
            navigationEventChannel.send(NavigationEvent.NavigateBack)
        }
    }

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnSubmit -> {
                viewModelScope.launch {
                    val state = _uiState.value
                    createNewPassword(
                        name = state.nameTextFieldState.text.toString(),
                        username = state.usernameTextFieldState.text.toString(),
                        website = state.websiteTextFieldState.text.toString(),
                        password = state.passwordTextFieldState.text.toString(),
                        note = state.notesTextFieldState.text.toString()
                    ).onSuccess {
                        insertVaultItem(it)
                        navigateUp()
                    }.onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                nameError = if (failure.contains(PasswordError.BlankName)) InputFieldError.Empty else null,
                                passwordError = if (failure.contains(PasswordError.BlankPassword)) InputFieldError.Empty else null
                            )
                        }
                    }
                }
            }

            is PasswordUiEvent.OnGeneratePasswordClick -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is PasswordUiEvent.OnBackClick -> navigateUp()

            is PasswordUiEvent.OnCloseBottomSheet -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is GeneratePasswordUiEvent -> super.onEvent(event)
        }
    }
}