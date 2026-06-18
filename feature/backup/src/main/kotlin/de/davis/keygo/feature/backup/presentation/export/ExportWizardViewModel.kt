package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FinishExportWizardError
import de.davis.keygo.feature.backup.domain.usecase.FinishExportWizardUseCase
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState
import de.davis.keygo.feature.backup.presentation.export.model.backupFileName
import de.davis.keygo.feature.backup.presentation.export.model.exportStepsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ExportWizardViewModel(
    private val backupDestinationResolver: BackupDestinationResolver,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val finishExportWizard: FinishExportWizardUseCase,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    private val passphraseTextFieldState = TextFieldState()
    private val confirmPassphraseTextFieldState = TextFieldState()

    private val _formatState = MutableStateFlow(SelectFormatState())
    private val _scheduleState = MutableStateFlow(SelectScheduleState())
    private val _destinationState = MutableStateFlow(SelectDestinationState())
    private val _providePassphraseState = MutableStateFlow(
        ProvidePassphraseState(
            passphraseTextFieldState = passphraseTextFieldState,
            confirmPassphraseTextFieldState = confirmPassphraseTextFieldState,
        )
    )

    private val _step = MutableStateFlow(ExportWizardStep.SelectFormat)

    val state = combine(
        _formatState,
        _scheduleState,
        _destinationState,
        _providePassphraseState,
        _step
    ) { formatState, scheduleState, destinationState, providePassphraseState, step ->
        ExportWizardUiState(
            formatState = formatState,
            scheduleState = scheduleState,
            destinationState = destinationState,
            providePassphraseState = providePassphraseState,
            step = step,
        )
    }
        .onStart {
            observePassphrase()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExportWizardUiState(
                formatState = _formatState.value,
                scheduleState = _scheduleState.value,
                destinationState = _destinationState.value,
                providePassphraseState = _providePassphraseState.value,
                step = _step.value,
            )
        )

    private val _event = Channel<ExportWizardEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePassphrase() {
        snapshotFlow {
            val passphrase = passphraseTextFieldState.text
            val valid =
                passphrase.isNotEmpty() && passphrase.contentEquals(confirmPassphraseTextFieldState.text)
            passphrase to valid
        }
            .debounce(150.milliseconds)
            .distinctUntilChanged()
            .mapLatest { (pwd, valid) -> passwordStrengthEstimator(pwd.toString()) to valid }
            .onEach { (score, valid) ->
                _providePassphraseState.update {
                    it.copy(
                        passphraseScore = score,
                        valid = valid
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ExportWizardUiEvent) {
        when (event) {
            ExportWizardUiEvent.Back -> previousStep()

            ExportWizardUiEvent.Continue -> {
                when {
                    _step.value == ExportWizardStep.Review -> finishExport()
                    else -> nextStep()
                }
            }

            ExportWizardUiEvent.ChooseDestination -> {
                val pickerEvent = when (_scheduleState.value.mode) {
                    ScheduleMode.Recurring -> ExportWizardEvent.PickFolder
                    ScheduleMode.OneTime -> ExportWizardEvent.CreateFile(
                        suggestedName = _formatState.value.format.backupFileName(),
                    )
                }
                _event.trySend(pickerEvent)
            }

            is ExportWizardUiEvent.FileFormatSelected -> {
                _formatState.update {
                    it.copy(format = event.format)
                }
                val recurringAllowed = event.format.encrypted
                _scheduleState.update {
                    it.copy(
                        recurringAllowed = recurringAllowed,
                        mode = if (!recurringAllowed && it.mode == ScheduleMode.Recurring) ScheduleMode.OneTime
                        else it.mode,
                    )
                }
                nextStep()
            }

            is ExportWizardUiEvent.ScheduleModeSelected -> _scheduleState.update {
                if (event.mode == ScheduleMode.Recurring && !it.recurringAllowed) it
                else it.copy(mode = event.mode)
            }

            is ExportWizardUiEvent.IntervalUnitSelected -> _scheduleState.update {
                it.copy(interval = it.interval.copy(unit = event.unit))
            }

            is ExportWizardUiEvent.IntervalCountChanged -> _scheduleState.update {
                it.copy(interval = it.interval.copy(count = event.count.coerceAtLeast(1)))
            }

            is ExportWizardUiEvent.KeepCountChanged -> _scheduleState.update {
                it.copy(keepCount = event.count.coerceAtLeast(1))
            }

            is ExportWizardUiEvent.KeepAllChanged -> _scheduleState.update {
                it.copy(keepAll = event.keepAll)
            }
        }
    }

    fun onDestinationPicked(uri: BackupDestinationUri?) {
        if (uri == null) return

        viewModelScope.launch {
            val destination = backupDestinationResolver.resolve(uri)

            _destinationState.update {
                it.copy(destination = destination, uri = uri)
            }
        }
    }

    private fun finishExport() {
        val details = currentExportDetails() ?: return

        viewModelScope.launch {
            finishExportWizard(details).onSuccess {
                _event.trySend(ExportWizardEvent.Finished)
            }.onFailure { error ->
                if (error == FinishExportWizardError.PassphraseEmpty)
                    _step.update { ExportWizardStep.ProvidePassphrase }

                val messageRes = when (error) {
                    FinishExportWizardError.PassphraseEmpty -> R.string.export_error_passphrase_empty
                    FinishExportWizardError.CryptoFailed -> R.string.export_error_crypto
                    FinishExportWizardError.SchedulePersistenceFailed -> R.string.export_error_schedule
                    FinishExportWizardError.DestinationPermissionDenied -> R.string.export_error_destination_permission
                }

                snackbarManager.sendMessage(
                    SnackbarMessage(message = ResourceString(messageRes)),
                )
            }
        }
    }

    private fun currentExportDetails(): ExportDetails? {
        val format = _formatState.value.format ?: return null
        val uri = _destinationState.value.uri ?: return null

        return ExportDetails(
            uri = uri,
            format = format,
            interval = _scheduleState.value
                .takeIf { it.mode == ScheduleMode.Recurring }
                ?.interval,
            passphrase = passphraseTextFieldState.text.toString(),
        )
    }

    private fun nextStep() = _step.update { current ->
        val steps = exportStepsFor(_formatState.value.format)
        steps[(steps.indexOf(current) + 1).coerceAtMost(steps.lastIndex)]
    }

    private fun previousStep() = _step.update { current ->
        val steps = exportStepsFor(_formatState.value.format)
        steps[(steps.indexOf(current) - 1).coerceAtLeast(0)]
    }
}