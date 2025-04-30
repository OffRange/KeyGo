package de.davis.keygo.dashboard.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.generated.RouteDestination

fun NavGraphBuilder.dashboardGraph() {
    composable<RouteDestination.Main.Home> {
        DashboardScreen()
    }
}