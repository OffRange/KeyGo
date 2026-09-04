package de.davis.keygo.feature.backup.presentation.export

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.security.presentation.rememberHandoffLauncher
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExportWizardScreen(navigateUp: () -> Unit) {
    val viewModel = koinViewModel<ExportWizardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val folderPicker = rememberHandoffLauncher(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onDestinationPicked(uri?.let { BackupDestinationUri(it.toString()) })
    }

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            ExportWizardEvent.Finished -> navigateUp()
            ExportWizardEvent.PickFolder -> folderPicker.launch(null)
        }
    }

    ExportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
