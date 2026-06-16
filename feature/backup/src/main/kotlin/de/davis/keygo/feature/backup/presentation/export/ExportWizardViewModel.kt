package de.davis.keygo.feature.backup.presentation.export

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState
import de.davis.keygo.feature.backup.presentation.export.model.exportStepsFor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
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
    private val backupDestinationResolver: BackupDestinationResolver
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

    private val _event = Channel<ExportWizardEvent>()
    val event = _event.receiveAsFlow()

    @OptIn(FlowPreview::class)
    private fun observePassphrase() {
        snapshotFlow {
            val passphrase = passphraseTextFieldState.text
            passphrase.isNotEmpty() && passphrase.contentEquals(confirmPassphraseTextFieldState.text)
        }
            .debounce(150.milliseconds)
            .distinctUntilChanged()
            .onEach { valid -> _providePassphraseState.update { it.copy(valid = valid) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ExportWizardUiEvent) {
        when (event) {
            ExportWizardUiEvent.Back -> previousStep()

            ExportWizardUiEvent.Continue -> nextStep()

            ExportWizardUiEvent.ChooseDestination -> {
                _event.trySend(ExportWizardEvent.OpenDestinationPicker)
            }

            // TODO: trigger the actual export once the export use case is wired up
            ExportWizardUiEvent.Export -> Unit

            is ExportWizardUiEvent.FileFormatSelected -> {
                _formatState.update {
                    it.copy(format = event.format)
                }
                nextStep()
            }

            is ExportWizardUiEvent.ScheduleModeSelected -> _scheduleState.update {
                it.copy(mode = event.mode)
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

    fun onDestinationPicked(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            val destination =
                backupDestinationResolver.resolve(BackupDestinationUri(uri.toString()))
            
            _destinationState.update {
                it.copy(destination = destination)
            }
        }
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