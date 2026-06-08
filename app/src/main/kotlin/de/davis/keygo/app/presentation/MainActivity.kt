package de.davis.keygo.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import de.davis.keygo.app.presentation.component.KeyGoNavigationWrapper
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.core.util.presentation.snackbar.SnackbarHandler
import de.davis.keygo.dashboard.presentation.DetailType
import de.davis.keygo.dashboard.presentation.dashboardGraph
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authGraph
import de.davis.keygo.feature.settings.presentation.ChangePasswordRoute
import de.davis.keygo.feature.settings.presentation.settingsGraph
import de.davis.keygo.item.dialog.SelectItemContent
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyGoTheme {
                val snackbarManager = koinInject<SnackbarManager>()
                CompositionLocalProvider(
                    LocalSnackbarManager provides snackbarManager,
                ) {
                    App()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "spec:width=673dp,height=841dp")
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "id:desktop_large")
@Composable
private fun App() {
    val listNavigator = rememberListDetailPaneScaffoldNavigator<DetailType>()
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showPrimaryActionButton = remember(currentDestination, listNavigator.currentDestination) {
        currentDestination
            ?.hierarchy
            ?.any { it.hasRoute<RouteDestination.Home.NavGraph>() == true } == true && !listNavigator.canNavigateBack()
    }

    val showChrome = remember(currentDestination, listNavigator.currentDestination) {
        currentDestination
            ?.hierarchy
            ?.any { it.hasRoute<RouteDestination.TopLevelAppGraph>() == true } == true && !listNavigator.canNavigateBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarHandler(snackbarHostState)

    val scope = rememberCoroutineScope()

    KeyGoNavigationWrapper(
        currentDestination = currentDestination,
        navigateToTopLevelDestination = {
            navController.navigate(it) {
                popUpTo<RouteDestination.TopLevelAppGraph> {
                    saveState = true
                }

                launchSingleTop = true
                restoreState = true
            }
        },
        onButtonClicked = {
            navController.navigate(RouteDestination.Home.SelectItem)
        },
        onItemSelected = { type ->
            scope.launch {
                listNavigator.navigateTo(
                    ThreePaneScaffoldRole.Primary,
                    DetailType.Modify.CreateNew(type)
                )
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
            startDestination = AuthRoute(),
        ) {
            authGraph(
                onSuccess = { totpUri ->
                    val dest = totpUri?.let {
                        RouteDestination.Home.Root(it)
                    } ?: RouteDestination.TopLevelAppGraph

                    navController.navigate(dest) {
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )

            navigation<RouteDestination.TopLevelAppGraph>(
                startDestination = RouteDestination.Home.NavGraph
            ) {
                navigation<RouteDestination.Home.NavGraph>(
                    startDestination = RouteDestination.Home.Root()
                ) {
                    dialog<RouteDestination.Home.SelectItem> {
                        SelectItemContent(
                            onSelect = {
                                scope.launch {
                                    navController.navigateUp()
                                    scope.launch {
                                        listNavigator.navigateTo(
                                            ThreePaneScaffoldRole.Primary,
                                            DetailType.Modify.CreateNew(it)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    dashboardGraph(listNavigator = listNavigator)
                }

                settingsGraph(
                    onOpenChangePassword = { navController.navigate(ChangePasswordRoute) },
                    onShowLibraries = { navController.navigate(RouteDestination.Libraries) },
                    onUp = { navController.navigateUp() },
                )
            }

            composable<RouteDestination.Libraries> {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val libs by produceLibraries()
                    LibrariesContainer(
                        libraries = libs,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
}
