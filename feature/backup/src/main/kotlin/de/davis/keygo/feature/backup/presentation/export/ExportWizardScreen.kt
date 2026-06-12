package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExportWizardScreen() {
    val viewModel = koinViewModel<ExportWizardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ExportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}