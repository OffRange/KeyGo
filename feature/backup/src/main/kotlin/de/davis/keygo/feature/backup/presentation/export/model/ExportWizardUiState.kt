package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod

@Stable
internal data class ExportWizardUiState(
    val formatState: SelectFormatState,
    val scheduleState: SelectScheduleState,
    val destinationState: SelectDestinationState,
    val providePassphraseState: ProvidePassphraseState,
    val step: ExportWizardStep = ExportWizardStep.SelectFormat,
) {
    val steps: List<ExportWizardStep> = exportStepsFor(formatState.format)

    val canContinue: Boolean = when (step) {
        ExportWizardStep.SelectDestination -> destinationState.destination != null
        ExportWizardStep.ProvidePassphrase ->
            providePassphraseState.method == EncryptionMethod.Ark || providePassphraseState.valid

        else -> true
    }

    val showsContinueButton: Boolean = step != ExportWizardStep.SelectFormat
}
