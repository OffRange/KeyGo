package de.davis.keygo.feature.backup.presentation.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun ImportWizardScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<ImportWizardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onFilePicked(uri?.let { BackupDestinationUri(it.toString()) })
    }

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ImportWizardEvent.PickFile -> filePicker.launch(
                arrayOf("application/json", "text/csv", "*/*"),
            )
        }
    }

    ImportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
