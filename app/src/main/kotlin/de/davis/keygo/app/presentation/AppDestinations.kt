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

enum class AppDestinations(
    val route: NavKey,
    @StringRes val label: Int,
    val icon: ImageVector,
    val intentAction: String? = null,
    @StringRes val shortLabel: Int = label,
) {
    HOME(RouteDestination.Home, R.string.home, Icons.Default.Home),
    PASSWORD_HEALTH(
        PasswordHealthRoute,
        R.string.password_health,
        Icons.Default.HealthAndSafety,
        ACTION_OPEN_PASSWORD_HEALTH,
        R.string.password_health_short,
    ),
    CONNECTIVITY(RouteDestination.Connectivity, R.string.connectivity, Icons.Default.Cast),
    SETTINGS(SettingsRoute, R.string.settings, Icons.Default.Settings);

    companion object {
        fun fromIntentAction(action: String?): AppDestinations? =
            action?.let { entries.firstOrNull { it.intentAction == action } }
    }
}

val TopLevelRoutes: Set<NavKey> = AppDestinations.entries.mapTo(LinkedHashSet()) { it.route }
