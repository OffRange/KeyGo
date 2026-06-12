package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ExportWizardViewModel : ViewModel() {

    private val passphraseTextFieldState = TextFieldState()
    private val confirmPassphraseTextFieldState = TextFieldState()

    private val _formatState = MutableStateFlow(SelectFormatState())
    private val _providePassphraseState = MutableStateFlow(
        ProvidePassphraseState(
            passphraseTextFieldState = passphraseTextFieldState,
            confirmPassphraseTextFieldState = confirmPassphraseTextFieldState,
        )
    )

    private val _step = MutableStateFlow(ExportWizardStep.SelectFormat)

    val state = combine(
        _formatState,
        _providePassphraseState,
        _step
    ) { formatState, providePassphraseState, step ->
        ExportWizardUiState(
            formatState = formatState,
            providePassphraseState = providePassphraseState,
            step = step,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExportWizardUiState(
            formatState = _formatState.value,
            providePassphraseState = _providePassphraseState.value,
            step = _step.value,
        )
    )

    fun onEvent(event: ExportWizardUiEvent) {

    }
}