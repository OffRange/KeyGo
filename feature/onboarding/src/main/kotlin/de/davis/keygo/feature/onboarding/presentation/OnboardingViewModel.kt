package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingStep
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import de.davis.keygo.migration.create_access.domain.usecase.HasMainPasswordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class OnboardingViewModel(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val hasV1Password: HasMainPasswordUseCase,
    private val accountRepository: AccountRepository,
    private val autofillServiceRepository: AutofillServiceRepository,
    private val chromeAutofillRepository: ChromeAutofillRepository,

    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createAccess: CreateAccessUseCase,
) : ViewModel() {

    private val stepsToSkip = MutableStateFlow<Set<OnboardingStep>>(emptySet())
    private val isMigrating = MutableStateFlow(false)

    init {
        calculateStepsToSkip()
    }

    private fun calculateStepsToSkip() {
        viewModelScope.launch {
            val hasAccount = accountRepository.getOrNull() != null
            val hasLegacyPassword = hasV1Password()

            val skipSteps = buildSet {
                if (!biometricAvailabilityRepository.availability()) add(OnboardingStep.EnableBiometrics)
                if (hasAccount || hasLegacyPassword) add(OnboardingStep.ImportExistingData)
                if (autofillServiceRepository.isEnabled() && chromeAutofillRepository.isAutofillEnabled())
                    add(OnboardingStep.EnableAutofillService)
            }
            stepsToSkip.update { skipSteps }
            isMigrating.update { hasLegacyPassword && !hasAccount }
        }
    }

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


    private val biometricChannel = Channel<Unit>()
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _step = MutableStateFlow(OnboardingStep.Welcome)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = _step.flatMapLatest {
        when (it) {
            OnboardingStep.Welcome -> isMigrating.mapLatest { migrating ->
                OnboardingUiState.Welcome(
                    migrating
                )
            }

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
        initialValue = OnboardingUiState.Welcome()
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
        when (_step.value) {
            OnboardingStep.SetMainPassword -> {
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

                if (OnboardingStep.EnableBiometrics in stepsToSkip.value) performCreateAccess()
            }

            OnboardingStep.EnableBiometrics -> {
                biometricChannel.trySend(Unit)
                return // wait for biometric result before proceeding to next step
            }

            else -> {}
        }

        internalSkip()
    }

    fun performCreateAccess(cipher: Cipher? = null) {
        viewModelScope.launch {
            loading {
                createAccess(
                    password = passwordTextFieldState.text.toString(),
                    biometricCipher = cipher
                ).onSuccess {
                    internalSkip()
                }
            }
        }
    }

    fun onSkip() {
        if (_step.value == OnboardingStep.EnableBiometrics) return performCreateAccess()

        internalSkip()
    }

    private fun internalSkip() {
        val nextStep = _step.value.nextStep(stepsToSkip.value) ?: return finishUp()
        _step.update { nextStep }
    }

    private fun finishUp() {

    }

    private suspend fun <R> loading(block: suspend () -> R): R {
        _loading.update { true }
        return block().also {
            _loading.update { false }
        }
    }
}
