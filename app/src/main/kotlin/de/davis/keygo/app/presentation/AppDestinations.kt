package de.davis.keygo.app.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.R
import de.davis.keygo.generated.RouteDestination
import de.davis.keygo.processor.annotation.Route

enum class AppDestinations(
    val route: RouteDestination,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val contentDescription: Int
) {
    HOME(RouteDestination.Main.Home, R.string.home, Icons.Default.Home, R.string.home),
    CONNECTIVITY(
        RouteDestination.Main.Connectivity,
        R.string.connectivity,
        Icons.Default.Cast,
        R.string.connectivity
    ),
    SETTINGS(
        RouteDestination.Main.Settings,
        R.string.settings,
        Icons.Default.Settings,
        R.string.settings
    ),
}

@Route(parent = "Main")
private fun Connectivity() {
}

@Route(parent = "Main")
private fun Settings() {
}