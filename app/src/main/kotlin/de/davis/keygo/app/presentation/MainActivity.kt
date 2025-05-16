package de.davis.keygo.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import de.davis.keygo.app.presentation.component.KeyGoNavigationWrapper
import de.davis.keygo.auth.presentation.authNavGraph
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.core.presentation.snackbar.SnackbarHandler
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import de.davis.keygo.dashboard.presentation.DashboardScreen
import de.davis.keygo.generated.RouteDestination
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyGoTheme {
                val snackbarManager = koinInject<SnackbarManager>()
                CompositionLocalProvider(LocalSnackbarManager provides snackbarManager) {
                    App()
                }
            }
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

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarManager = LocalSnackbarManager.current
    SnackbarHandler(snackbarManager, snackbarHostState)

    val scope = rememberCoroutineScope()
    KeyGoNavigationWrapper(
        currentDestination = currentDestination,
        navigateToTopLevelDestination = navigationActions::navigateTo,
        onButtonClicked = {
            scope.launch {
                snackbarManager.sendMessage(SnackbarMessage("${System.currentTimeMillis()}".asUIText()))
            }
        },
        showChrome = showChrome,
        showPrimaryActionButton = showPrimaryActionButton,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = RouteDestination.Auth,
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
                mainGraph(navigate = navigationActions::navigateTo)

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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun NavGraphBuilder.mainGraph(navigate: (RouteDestination) -> Unit) {
    composable<RouteDestination.Main.Home> {
        val navigator = rememberListDetailPaneScaffoldNavigator<VaultItem>()
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    DashboardScreen(navigate = navigate)
                }
            },
            detailPane = {
                AnimatedPane {
                    Surface {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "text")
                        }
                    }
                }
            }
        )
    }
}
