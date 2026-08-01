package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.component.Wizard
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState

@Composable
internal fun ExportWizardContent(
    state: ExportWizardUiState,
    onEvent: (ExportWizardUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    Wizard(
        steps = state.steps,
        currentStep = state.step,
        title = state.step.title,
        onBack = { onEvent(ExportWizardUiEvent.Back) },
        onContinue = { onEvent(ExportWizardUiEvent.Continue) },
        navigateUp = navigateUp,
        showContinueButton = state.showsContinueButton,
        canContinue = state.canContinue,
        continueButtonContent = {
            val recurring = state.scheduleState.mode == ScheduleMode.Recurring
            val isReview = state.step == ExportWizardStep.Review
            AnimatedVisibility(
                visible = isReview
            ) {
                Icon(
                    imageVector = if (recurring) Icons.Default.Schedule else Icons.Default.Backup,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize),
                )
            }
            Text(
                text = stringResource(
                    when {
                        isReview && recurring -> R.string.schedule_backup
                        isReview -> R.string.create_backup
                        else -> R.string.continue_step
                    }
                ),
            )
        },
    ) { step ->
        when (step) {
            ExportWizardStep.SelectFormat -> SelectFileFormatContent(onEvent = onEvent)
            ExportWizardStep.Schedule -> SelectScheduleContent(
                state = state.scheduleState,
                onEvent = onEvent,
            )

            ExportWizardStep.SelectDestination -> SelectDestinationContent(
                state = state.destinationState,
                scheduleState = state.scheduleState,
                format = state.formatState.format,
                onEvent = onEvent,
            )

            ExportWizardStep.ProvidePassphrase -> ProvidePassphraseContent(
                state = state.providePassphraseState,
                onEvent = onEvent,
            )

            ExportWizardStep.SelectCsvPreset -> SelectCsvPresetContent(
                preset = state.formatState.csvPreset,
                onEvent = onEvent,
            )

            ExportWizardStep.Review -> state.formatState.format?.let { format ->
                ReviewBackupContent(
                    format = format,
                    scheduleState = state.scheduleState,
                    destinationState = state.destinationState,
                    passphraseState = state.providePassphraseState,
                    csvPreset = state.formatState.csvPreset,
                )
            }
        }
    }
}

private val ExportWizardStep.title
    @Composable
    get() = when (this) {
        ExportWizardStep.SelectFormat -> stringResource(R.string.select_file_format_title)
        ExportWizardStep.Schedule -> stringResource(R.string.select_schedule_title)
        ExportWizardStep.SelectDestination -> stringResource(R.string.select_destination_title)
        ExportWizardStep.ProvidePassphrase -> stringResource(R.string.provide_passphrase_title)
        ExportWizardStep.SelectCsvPreset -> stringResource(R.string.select_csv_preset_title)
        ExportWizardStep.Review -> stringResource(R.string.review_backup_title)
    }

private class ExportWizardUiStateProvider : PreviewParameterProvider<ExportWizardUiState> {

    override val values = ExportWizardStep.entries.asSequence().map {
        ExportWizardUiState(
            formatState = SelectFormatState(
                format = if (it == ExportWizardStep.SelectCsvPreset) FileFormat.CSV else FileFormat.JSON,
            ),
            scheduleState = SelectScheduleState(),
            destinationState = SelectDestinationState(
                destination = BackupDestination(
                    provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
                    displayPath = "Backups / KeyGo",
                ),
            ),
            providePassphraseState = ProvidePassphraseState(
                passphraseTextFieldState = TextFieldState(),
                confirmPassphraseTextFieldState = TextFieldState(),
                passphraseScore = PasswordScore.Strong,
            ),
            step = it,
        )
    }
}

@Preview
@Composable
private fun ExportWizardContentPreview(@PreviewParameter(ExportWizardUiStateProvider::class) state: ExportWizardUiState) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            ExportWizardContent(
                state = state,
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}