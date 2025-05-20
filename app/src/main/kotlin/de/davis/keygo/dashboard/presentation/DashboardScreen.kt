package de.davis.keygo.dashboard.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.dashboard.presentation.model.DashboardNavEvent
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DashboardScreen(navigate: (RouteDestination) -> Unit) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runSearch()
    }

    LaunchedEffect(uiState.navEvent) {
        when (uiState.navEvent) {
            is DashboardNavEvent.None -> {}
        }
    }

    DashboardContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}