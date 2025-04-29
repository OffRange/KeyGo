package de.davis.keygo.app.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.R
import de.davis.keygo.generated.Route

enum class AppDestinations(
    val route: Route,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val contentDescription: Int
) {
    HOME(Route.Main.Home, R.string.home, Icons.Default.Home, R.string.home),
    CONNECTIVITY(
        Route.Main.Connectivity,
        R.string.connectivity,
        Icons.Default.Cast,
        R.string.connectivity
    ),
    SETTINGS(Route.Main.Settings, R.string.settings, Icons.Default.Settings, R.string.settings),
}

@de.davis.keygo.processor.annotation.Route(parent = "Main")
private fun Connectivity() {
}

@de.davis.keygo.processor.annotation.Route(parent = "Main")
private fun Settings() {
}