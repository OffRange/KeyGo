package de.davis.keygo.feature.backup.presentation.export

import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.security.presentation.rememberHandoffLauncher
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import org.koin.androidx.compose.koinViewModel

private const val TAG = "ExportWizardScreen"

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
            ExportWizardEvent.PickFolder -> folderPicker.launch(null).onFailure {
                Log.w(TAG, "No activity found to handle the folder picker", it)
            }
        }
    }

    ExportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
        navigateUp = navigateUp,
    )
}
