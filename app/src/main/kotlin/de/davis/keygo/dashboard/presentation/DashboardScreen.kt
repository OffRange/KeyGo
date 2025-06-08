package de.davis.keygo.dashboard.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.davis.keygo.core.presentation.model.RouteDestination
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(navigate: (RouteDestination) -> Unit) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runSearch()
    }

    DashboardContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}