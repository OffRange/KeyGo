package de.davis.keygo.dashboard.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.generated.RouteDestination

fun NavGraphBuilder.dashboardGraph(navigate: (RouteDestination) -> Unit) {
    composable<RouteDestination.Main.Home> {
        DashboardScreen(navigate = navigate)
    }
}