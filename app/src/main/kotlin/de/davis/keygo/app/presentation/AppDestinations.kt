package de.davis.keygo.app.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.R
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.feature.password_health.presentation.ACTION_OPEN_PASSWORD_HEALTH
import de.davis.keygo.feature.password_health.presentation.PasswordHealthRoute
import de.davis.keygo.feature.settings.presentation.SettingsRoute

/** The navigation bar's destinations. */
enum class AppDestinations(
    val route: NavKey,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val contentDescription: Int,
    val intentAction: String? = null,
) {
    HOME(RouteDestination.Home, R.string.home, Icons.Default.Home, R.string.home),
    PASSWORD_HEALTH(
        PasswordHealthRoute,
        R.string.password_health,
        Icons.Default.HealthAndSafety,
        R.string.password_health,
        ACTION_OPEN_PASSWORD_HEALTH,
    ),
    CONNECTIVITY(
        RouteDestination.Connectivity,
        R.string.connectivity,
        Icons.Default.Cast,
        R.string.connectivity
    ),
    SETTINGS(
        SettingsRoute,
        R.string.settings,
        Icons.Default.Settings,
        R.string.settings
    ),
    ;

    companion object {
        fun fromIntentAction(action: String?): AppDestinations? =
            action?.let { entries.firstOrNull { it.intentAction == action } }
    }
}

val TopLevelRoutes: Set<NavKey> = AppDestinations.entries.mapTo(LinkedHashSet()) { it.route }
