package de.davis.keygo.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import de.davis.keygo.app.presentation.component.KeyGoNavigationWrapper
import de.davis.keygo.auth.presentation.authNavGraph
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import de.davis.keygo.dashboard.presentation.dashboardGraph
import de.davis.keygo.generated.RouteDestination

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

private class NavigationActions(private val navController: NavHostController) {

    fun navigateTo(route: RouteDestination) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }

            launchSingleTop = true
            restoreState = true
        }
    }
}

@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "spec:width=673dp,height=841dp")
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "id:desktop_large")
@Composable
private fun App() {
    KeyGoTheme {
        val navController = rememberNavController()
        val navigationActions = remember(navController) {
            NavigationActions(navController)
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val showChrome = remember(currentDestination) {
            currentDestination?.parent?.hasRoute<RouteDestination.Main.Root>() == true
        }

        val showPrimaryActionButton = remember(currentDestination) {
            currentDestination?.hasRoute<RouteDestination.Main.Home>() == true
        }

        KeyGoNavigationWrapper(
            currentDestination = currentDestination,
            navigateToTopLevelDestination = navigationActions::navigateTo,
            onButtonClicked = {},
            showChrome = showChrome,
            showPrimaryActionButton = showPrimaryActionButton
        ) {
            NavHost(
                navController = navController,
                startDestination = RouteDestination.Auth
            ) {
                authNavGraph(
                    onSuccess = {
                        navController.navigate(RouteDestination.Main.Root) {
                            popUpTo(RouteDestination.Auth) { inclusive = true }
                        }
                    }
                )
                navigation<RouteDestination.Main.Root>(
                    startDestination = RouteDestination.Main.Home
                ) {
                    dashboardGraph()

                    composable<RouteDestination.Main.Connectivity> {
                        Text("CONNECTIVITY")
                    }
                    composable<RouteDestination.Main.Settings> {
                        Text("SETTINGS")
                    }
                }
            }
        }
    }
}
