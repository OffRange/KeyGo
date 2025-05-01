package de.davis.keygo.dashboard.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.dashboard.presentation.model.DashboardNavEvent
import de.davis.keygo.generated.RouteDestination
import de.davis.keygo.processor.annotation.Route
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Route(name = "Home", parent = "Main")
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

    val navigator = rememberListDetailPaneScaffoldNavigator<VaultItem>()
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                DashboardContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                Surface {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "text")
                    }
                }
            }
        }
    )
}