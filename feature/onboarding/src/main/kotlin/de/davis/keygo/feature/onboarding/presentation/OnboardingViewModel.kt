package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingStep
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class OnboardingViewModel(
    private val passwordStrengthEstimator: PasswordStrengthEstimator
) : ViewModel() {

    private val passwordTextFieldState = TextFieldState()
    private val confirmPasswordTextFieldState = TextFieldState()

    private val _mainPasswordState = MutableStateFlow(
        OnboardingUiState.SetMainPassword(
            passwordTextFieldState = passwordTextFieldState,
            confirmPasswordTextFieldState = confirmPasswordTextFieldState,
            passwordScore = PasswordScore.None
        )
    )

    private val _enableBiometricsState = MutableStateFlow(
        OnboardingUiState.EnableBiometrics
    )

    private val _importDataState = MutableStateFlow(
        OnboardingUiState.ImportData
    )

    private val _enableAutofillState = MutableStateFlow(
        OnboardingUiState.EnableAutofill
    )

    private val _step = MutableStateFlow(OnboardingStep.Welcome)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = _step.flatMapLatest {
        when (it) {
            OnboardingStep.Welcome -> flowOf(OnboardingUiState.Welcome)
            OnboardingStep.SetMainPassword -> _mainPasswordState
            OnboardingStep.EnableBiometrics -> _enableBiometricsState
            OnboardingStep.ImportExistingData -> _importDataState
            OnboardingStep.EnableAutofillService -> _enableAutofillState
        }
    }.onStart {
        observePasswordStrength()
        observePasswordError()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState.Welcome
    )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordStrength() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { passwordStrengthEstimator(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                _mainPasswordState.update {
                    it.copy(passwordScore = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordError() {
        combine(
            snapshotFlow { passwordTextFieldState.text },
            snapshotFlow { confirmPasswordTextFieldState.text }
        ) { pwd, confirmation ->
            pwd.trim() to (pwd == confirmation)
        }.debounce(150.milliseconds)
            .distinctUntilChanged()
            .onEach { (password, isEqual) ->
                if (password.isNotBlank()) {
                    _mainPasswordState.update {
                        it.copy(passwordError = null)
                    }
                }

                if (isEqual) {
                    _mainPasswordState.update {
                        it.copy(confirmPasswordError = null)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onNextStep() {
        val currentStep = _step.value
        if (currentStep == OnboardingStep.SetMainPassword) {
            val password = passwordTextFieldState.text.toString()
            val confirmPassword = confirmPasswordTextFieldState.text.toString()

            if (password.isBlank()) {
                _mainPasswordState.update {
                    it.copy(passwordError = UiFieldError.Empty)
                }
                return
            }

            if (password != confirmPassword) {
                _mainPasswordState.update {
                    it.copy(confirmPasswordError = UiFieldError.Mismatch)
                }
                return
            }
        }

        val nextStep = currentStep.nextStep() ?: return finishUp()
        _step.update { nextStep }
    }

    private fun finishUp() {

    }
}