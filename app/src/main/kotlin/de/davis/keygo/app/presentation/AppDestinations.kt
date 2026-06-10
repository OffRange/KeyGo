package de.davis.keygo.app.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.R
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.RouteDestination as UiRouteDestination
import de.davis.keygo.feature.settings.presentation.SettingsGraphRoute

enum class AppDestinations(
    val route: UiRouteDestination,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val contentDescription: Int
) {
    HOME(RouteDestination.Home.NavGraph, R.string.home, Icons.Default.Home, R.string.home),
    CONNECTIVITY(
        RouteDestination.Connectivity,
        R.string.connectivity,
        Icons.Default.Cast,
        R.string.connectivity
    ),
    SETTINGS(
        SettingsGraphRoute,
        R.string.settings,
        Icons.Default.Settings,
        R.string.settings
    ),
}