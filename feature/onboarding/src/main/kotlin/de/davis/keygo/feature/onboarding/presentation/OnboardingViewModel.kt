package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.onboarding.presentation.model.AutofillSetupAction
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingStep
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingStepProgress
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class OnboardingViewModel(
    @InjectedParam private val onboardingRoute: OnboardingRoute,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val autofillServiceRepository: AutofillServiceRepository,
    private val chromeAutofillRepository: ChromeAutofillRepository,

    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createAccess: CreateAccessUseCase,
) : ViewModel() {

    private val hasPendingTotpImport = onboardingRoute.uri != null

    private val stepsToSkip = MutableStateFlow<Set<OnboardingStep>>(emptySet())

    private fun calculateStepsToSkip() {
        viewModelScope.launch {
            val autofill = readAutofillState()
            _enableAutofillState.update { autofill }

            val skipSteps = buildSet {
                if (!biometricAvailabilityRepository.availability()) add(OnboardingStep.EnableBiometrics)
                // Not "both enabled": on a device with no Chrome the Chrome read is false forever,
                // which would keep offering a step that has nothing left to do.
                if (autofill.nextAction == AutofillSetupAction.Finish)
                    add(OnboardingStep.EnableAutofillService)
            }
            stepsToSkip.update { skipSteps }
        }
    }

    private suspend fun readAutofillState(): OnboardingUiState.EnableAutofill {
        val chromeAvailable = chromeAutofillRepository.isAvailable()
        return OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = autofillServiceRepository.isEnabled(),
            chromeAvailable = chromeAvailable,
            chromeAutofillEnabled = chromeAvailable && chromeAutofillRepository.isAutofillEnabled(),
        )
    }

    fun refreshAutofillState() {
        viewModelScope.launch {
            val autofill = readAutofillState()
            _enableAutofillState.update { autofill }
        }
    }

    private val passwordTextFieldState = TextFieldState()
    private val confirmPasswordTextFieldState = TextFieldState()

    private val _enableBiometricsState = MutableStateFlow(
        OnboardingUiState.EnableBiometrics
    )

    private val _importDataState = MutableStateFlow(
        OnboardingUiState.ImportData()
    )

    private val _enableAutofillState = MutableStateFlow(
        OnboardingUiState.EnableAutofill()
    )

    // Declared after every property calculateStepsToSkip() touches. init runs in declaration
    // order, and viewModelScope is Main.immediate, so on the main thread this launch body starts
    // executing synchronously; if it ran before _enableAutofillState above it would read a
    // not-yet-initialized property.
    init {
        calculateStepsToSkip()
    }

    private val biometricChannel = Channel<Unit>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val autofillPickerChannel = Channel<Unit>(Channel.BUFFERED)
    val autofillPickerFlow = autofillPickerChannel.receiveAsFlow()

    private val finishedChannel = Channel<Unit>(Channel.BUFFERED)
    val finishedFlow = finishedChannel.receiveAsFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _passwordError = MutableStateFlow<UiFieldError?>(null)
    private val _confirmPasswordError = MutableStateFlow<UiFieldError?>(null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val passwordScoreFlow = snapshotFlow { passwordTextFieldState.text }
        .debounce(150.milliseconds)
        .mapLatest { passwordStrengthEstimator(it.toString()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val _mainPasswordState = combine(
        snapshotFlow { passwordTextFieldState.text },
        snapshotFlow { confirmPasswordTextFieldState.text },
        passwordScoreFlow,
        _passwordError,
        _confirmPasswordError
    ) { pwd, confirm, score, manualPwdError, manualConfirmError ->
        // Automatically clear manual errors if the user has fixed them by typing
        val resolvedPwdError = if (pwd.isNotBlank()) null else manualPwdError
        val resolvedConfirmError = if (pwd == confirm) null else manualConfirmError

        OnboardingUiState.SetMainPassword(
            passwordTextFieldState = passwordTextFieldState,
            confirmPasswordTextFieldState = confirmPasswordTextFieldState,
            passwordScore = score,
            passwordError = resolvedPwdError,
            confirmPasswordError = resolvedConfirmError
        )
    }

    private val _step = MutableStateFlow(OnboardingStep.Welcome)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = _step.flatMapLatest {
        when (it) {
            OnboardingStep.Welcome -> flowOf(OnboardingUiState.Welcome(pendingTotpImport = hasPendingTotpImport))

            OnboardingStep.SetMainPassword -> _mainPasswordState
            OnboardingStep.EnableBiometrics -> _enableBiometricsState
            OnboardingStep.ImportExistingData -> _importDataState
            OnboardingStep.EnableAutofillService -> _enableAutofillState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState.Welcome()
    )

    val stepProgress = combine(_step, stepsToSkip) { step, skip ->
        val activeSteps = OnboardingStep.activeSteps(skip)
        OnboardingStepProgress(
            currentIndex = activeSteps.indexOf(step),
            totalSteps = activeSteps.size,
            canGoBack = step.canGoBack,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingStepProgress(
            currentIndex = 0,
            totalSteps = OnboardingStep.entries.size,
            canGoBack = false,
        ),
    )

    fun onPreviousStep() {
        val previous = _step.value.previousStep(stepsToSkip.value) ?: return

        // fileUri survives onImportFinished by design (see its own comment), so stepping back
        // from autofill into a completed import would otherwise reopen the wizard on the old
        // file instead of the chooser. Only autofill's back reaches this step, so this never
        // fires while the chooser itself is still on screen.
        if (previous == OnboardingStep.ImportExistingData)
            _importDataState.update { it.copy(fileUri = null) }

        _step.update { previous }
    }

    fun onNextStep() {
        when (_step.value) {
            OnboardingStep.SetMainPassword -> {
                val password = passwordTextFieldState.text.toString()
                val confirmPassword = confirmPasswordTextFieldState.text.toString()

                if (password.isBlank()) {
                    _passwordError.update { UiFieldError.Empty }
                    return
                }

                if (password != confirmPassword) {
                    _confirmPasswordError.update { UiFieldError.Mismatch }
                    return
                }

                if (OnboardingStep.EnableBiometrics in stepsToSkip.value) return performCreateAccess()
            }

            OnboardingStep.EnableBiometrics -> {
                biometricChannel.trySend(Unit)
                return // wait for biometric result before proceeding to next step
            }

            OnboardingStep.EnableAutofillService -> when (_enableAutofillState.value.nextAction) {
                AutofillSetupAction.OpenSystemSettings -> {
                    autofillPickerChannel.trySend(Unit)
                    return // wait for the user to come back from the system picker
                }

                AutofillSetupAction.OpenChromeSettings -> {
                    chromeAutofillRepository.openChromeAutofillSettings()
                    return // wait for the user to come back from Chrome
                }

                // Nothing left to set up, fall through to the step advance below.
                AutofillSetupAction.Finish -> {}
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

    fun onImportFileChosen(uri: BackupDestinationUri) =
        _importDataState.update { it.copy(fileUri = uri) }

    fun onImportCancelled() = _importDataState.update { it.copy(fileUri = null) }

    /**
     * The wizard reports its own outcome, so onboarding only has to move on.
     *
     * Deliberately does not clear `fileUri` first. `state` derives from `_step.flatMapLatest`, so
     * advancing `_step` here is what swaps `state` straight to the next step's flow. Clearing
     * `fileUri` before that swap would emit `ImportData(null)` while `_step` still pointed at
     * `ImportExistingData`, flashing the chooser card between the summary and the next step.
     */
    fun onImportFinished() = internalSkip()

    private fun internalSkip() {
        val nextStep = _step.value.nextStep(stepsToSkip.value) ?: return finishUp()
        _step.update { nextStep }
    }

    private fun finishUp() {
        finishedChannel.trySend(Unit)
    }

    private suspend fun <R> loading(block: suspend () -> R): R {
        _loading.update { true }
        try {
            return block()
        } finally {
            _loading.update { false }
        }
    }
}
