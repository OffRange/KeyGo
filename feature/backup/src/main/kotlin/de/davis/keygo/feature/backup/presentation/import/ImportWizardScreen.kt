package de.davis.keygo.feature.backup.presentation.import

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardEvent
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiEvent
import org.koin.androidx.compose.koinViewModel

/**
 * @param navigateUp what leaving the wizard does. From the backup hub this pops the screen; when a
 * host preselected a file, [preselectedFile] changes what this means to "hand the file choice back
 * to the host" instead.
 * @param preselectedFile a file the host picked before opening the wizard. When set, the wizard
 * skips its own file step and [navigateUp] means "give the file choice back to the host" rather
 * than "leave the screen".
 * @param onFinished what the summary's Done button does. Defaults to [navigateUp], which is what
 * the backup hub wants.
 */
@Composable
fun ImportWizardScreen(
    navigateUp: () -> Unit,
    preselectedFile: BackupDestinationUri? = null,
    onFinished: () -> Unit = navigateUp,
) {
    val viewModel = koinViewModel<ImportWizardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val chooseFile = rememberImportFilePicker(viewModel::onFilePicked)

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ImportWizardEvent.PickFile -> chooseFile.launch()

            ImportWizardEvent.Exit -> navigateUp()
        }
    }

    LaunchedEffect(preselectedFile) {
        if (preselectedFile != null) viewModel.seedFile(preselectedFile)
    }

    // seedFile lands on the next composition, so for one frame the state still says the wizard owns
    // file selection. Showing the reading phase for that frame keeps the chooser in one place.
    val displayState = if (preselectedFile != null && !state.fileChosenByHost)
        state.copy(progress = ImportProgress.Reading)
    else state

    // While a host owns the file, the wizard is the only thing that may answer back: it has no back
    // stack of its own to fall through to, and an unhandled press would finish the host's Activity
    // mid import. So this stays enabled for every phase and swallows the press where back is inert,
    // rather than letting the host compensate with a no-op handler of its own. Wizard's
    // PredictiveBackHandler is composed deeper and still wins wherever it is enabled.
    BackHandler(enabled = preselectedFile != null) {
        if (displayState.backEnabled) viewModel.onEvent(ImportWizardUiEvent.Back)
    }

    // Wizard's own toolbar arrow calls navigateUp directly on the first step it renders, bypassing
    // onBack(). A seeded wizard needs that arrow on the same path as the back gesture above, or
    // tapping it would leave a running import and the seed in place instead of tearing them down.
    val onBackToHost: () -> Unit = { viewModel.onEvent(ImportWizardUiEvent.Back) }

    ImportWizardContent(
        state = displayState,
        onEvent = viewModel::onEvent,
        navigateUp = if (preselectedFile != null) onBackToHost else navigateUp,
        onFinished = onFinished,
    )
}
