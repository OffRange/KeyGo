package de.davis.keygo.dashboard.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.generated.Route

fun NavGraphBuilder.dashboardGraph() {
    composable<Route.Main.Home> {
        DashboardScreen()
    }
}