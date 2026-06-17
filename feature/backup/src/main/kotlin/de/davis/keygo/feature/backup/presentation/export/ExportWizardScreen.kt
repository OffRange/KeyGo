package de.davis.keygo.feature.backup.presentation.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExportWizardScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<ExportWizardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onDestinationPicked(uri?.let { BackupDestinationUri(it.toString()) })
    }

    // CreateDocument fixes its MIME type at construction. We use a generic binary
    // type for both formats and let the suggested file name carry the extension
    // (.kdbx / .csv); the user can still rename in the system dialog.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        viewModel.onDestinationPicked(uri?.let { BackupDestinationUri(it.toString()) })
    }

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ExportWizardEvent.Finished -> navigateUp()
            ExportWizardEvent.PickFolder -> folderPicker.launch(null)
            is ExportWizardEvent.CreateFile -> filePicker.launch(it.suggestedName)
        }
    }

    ExportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
