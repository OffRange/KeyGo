package de.davis.keygo.feature.settings.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.feature.settings.presentation.changepassword.ChangePasswordScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsGraphRoute : RouteDestination

@Serializable
internal object SettingsHomeRoute : RouteDestination {
    override val graphDest: RouteDestination get() = SettingsGraphRoute
}

@Serializable
object ChangePasswordRoute : RouteDestination {
    override val graphDest: RouteDestination get() = SettingsGraphRoute
}

fun NavGraphBuilder.settingsGraph(
    onOpenChangePassword: () -> Unit,
    onShowLibraries: () -> Unit,
    onOpenBackup: () -> Unit,
    onUp: () -> Unit,
) = navigation<SettingsGraphRoute>(startDestination = SettingsHomeRoute) {
    composable<SettingsHomeRoute> {
        SettingsScreen(
            showLibraries = onShowLibraries,
            onOpenChangePassword = onOpenChangePassword,

            // TODO: #51
            onExportDataClicked = onOpenBackup,
            onImportDataClicked = {}
        )
    }
    composable<ChangePasswordRoute> {
        ChangePasswordScreen(onUp = onUp)
    }
}
