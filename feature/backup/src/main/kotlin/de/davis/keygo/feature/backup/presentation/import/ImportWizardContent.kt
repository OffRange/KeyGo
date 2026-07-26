package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportSummary
import de.davis.keygo.feature.backup.presentation.component.BackupFileChooser
import de.davis.keygo.feature.backup.presentation.component.Wizard
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardStep
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiState
import kotlinx.coroutines.delay

@Composable
internal fun ImportWizardContent(
    state: ImportWizardUiState,
    onEvent: (ImportWizardUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    AnimatedContent(
        targetState = state.progress,
        contentKey = { it.phaseKey },
        label = "importPhase",
    ) { progress ->
        when (progress) {
            is ImportProgress.Succeeded -> ImportResultContent(
                summary = progress.summary,
                onDone = navigateUp,
            )

            is ImportProgress.Failed -> ImportErrorContent(
                error = progress.error,
                onBack = { onEvent(ImportWizardUiEvent.Back) },
            )

            ImportProgress.Reading,
            ImportProgress.Parsing,
            is ImportProgress.Running -> ImportRunningContent(progress = progress)

            null -> InputWizard(state = state, onEvent = onEvent, navigateUp = navigateUp)
        }
    }
}

/**
 * Groups [ImportProgress] into the distinct screens the wizard renders so that
 * [AnimatedContent] only transitions when the screen actually changes. In particular the
 * running phases share one key, letting [ImportProgress.Running] tick its progress without
 * re-triggering the enter/exit animation.
 */
private val ImportProgress?.phaseKey: Int
    get() = when (this) {
        null -> 0
        ImportProgress.Reading,
        ImportProgress.Parsing -> 1

        is ImportProgress.Running -> 2

        is ImportProgress.Succeeded -> 3
        is ImportProgress.Failed -> 4
    }

@Composable
private fun InputWizard(
    state: ImportWizardUiState,
    onEvent: (ImportWizardUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    Wizard(
        steps = state.steps,
        currentStep = state.step,
        title = state.step.title,
        onBack = { onEvent(ImportWizardUiEvent.Back) },
        onContinue = { onEvent(ImportWizardUiEvent.Continue) },
        navigateUp = navigateUp,
        showContinueButton = state.showContinueButton,
        canContinue = state.canContinue,
        continueButtonContent = {
            Text(text = stringResource(R.string.continue_step))
        },
    ) { step ->
        when (step) {
            ImportWizardStep.SelectFile -> Column(modifier = Modifier.fillMaxSize()) {
                BackupFileChooser(
                    destination = state.backupDestination,
                    onChoose = { onEvent(ImportWizardUiEvent.ChooseFile) },
                    chooserIcon = Icons.Default.FileOpen,
                    chooserTitle = stringResource(R.string.import_choose_title),
                    chooserSubtitle = stringResource(R.string.import_choose_subtitle),
                    chooserAction = stringResource(R.string.import_choose_action),
                    changeLabel = stringResource(R.string.file_chooser_change),
                    fileNameLabel = stringResource(
                        R.string.import_selected_label,
                        state.backupDestination?.fileName.orEmpty()
                    ),
                )
            }

            ImportWizardStep.MapColumns -> MapColumnsContent(
                columns = state.columns,
                duplicateTypes = state.duplicateTypes,
                fileName = state.backupDestination?.fileName,
                onTypeChange = { index, type ->
                    onEvent(ImportWizardUiEvent.ChangeColumnType(index, type))
                },
            )

            ImportWizardStep.ProvidePassphrase -> ProvidePassphraseContent(
                passphraseState = state.passphraseState,
                isError = state.passphraseError,
            )

            ImportWizardStep.SelectVault -> SelectVaultContent(
                vaults = state.vaults,
                selectedVaultId = state.selectedVaultId,
                creatingNewVault = state.creatingNewVault,
                newVaultNameState = state.newVaultNameState,
                newVaultIcon = state.newVaultIcon,
                onSelectVault = { onEvent(ImportWizardUiEvent.SelectVault(it)) },
                onCreateNewVault = { onEvent(ImportWizardUiEvent.CreateNewVault) },
                onSelectNewVaultIcon = {
                    onEvent(ImportWizardUiEvent.SelectNewVaultIcon(it))
                },
            )
        }
    }
}

private val ImportWizardStep.title
    @Composable
    get() = when (this) {
        ImportWizardStep.SelectFile -> stringResource(R.string.select_import_file_title)
        ImportWizardStep.MapColumns -> stringResource(R.string.map_columns_title)
        ImportWizardStep.SelectVault -> stringResource(R.string.select_vault_title)
        ImportWizardStep.ProvidePassphrase -> stringResource(R.string.provide_passphrase_title)
    }

private class ImportWizardUiStateProvider : PreviewParameterProvider<ImportWizardUiState> {

    override val values = ImportWizardStep.entries.asSequence().map { step ->
        when (step) {
            ImportWizardStep.MapColumns -> ImportWizardUiState(
                step = step,
                backupDestination = BackupDestination(
                    provider = BackupDestination.Provider.OnDevice,
                    displayPath = "Downloads",
                    fileName = "passwords.csv",
                ),
                columns = previewColumnRows,
                duplicateTypes = setOf(CsvColumnType.Url),
            )

            else -> ImportWizardUiState(step = step)
        }
    } + sequenceOf(
        ImportWizardUiState(progress = ImportProgress.Reading),
        ImportWizardUiState(progress = ImportProgress.Failed(ImportError.NothingImported)),
        ImportWizardUiState(progress = ImportProgress.Parsing),
        ImportWizardUiState(progress = ImportProgress.Running(1, 10)),
        ImportWizardUiState(
            progress = ImportProgress.Succeeded(
                ImportSummary(
                    imported = 10,
                    skipped = 2,
                    failed = 3,
                    vaultsCreated = 4
                )
            )
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ImportWizardTransitionPreview() {
    val phases = remember {
        listOf(
            ImportProgress.Reading,
            ImportProgress.Parsing,
            ImportProgress.Running(3, 10),
            ImportProgress.Succeeded(
                ImportSummary(imported = 10, skipped = 2, failed = 3, vaultsCreated = 4),
            ),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            index = (index + 1) % phases.size
        }
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ImportWizardContent(
                state = ImportWizardUiState(progress = phases[index]),
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}

@Preview
@Composable
private fun ImportWizardContentPreview(@PreviewParameter(ImportWizardUiStateProvider::class) state: ImportWizardUiState) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ImportWizardContent(
                state = state,
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}
