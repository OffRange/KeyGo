package de.davis.keygo.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import de.davis.keygo.app.presentation.component.KeyGoNavigationWrapper
import de.davis.keygo.auth.presentation.authNavGraph
import de.davis.keygo.core.domain.annotation.OneTimeUsage
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.domain.model.navigation.NavigationAction
import de.davis.keygo.core.domain.navigation.Navigator
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.ObserveAsEvents
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.presentation.navigation.LocalNavigator
import de.davis.keygo.core.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.core.presentation.snackbar.SnackbarHandler
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import de.davis.keygo.dashboard.presentation.DashboardScreen
import de.davis.keygo.item.presentation.dialog.SelectItemContent
import de.davis.keygo.item.presentation.password.PasswordScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyGoTheme {
                val snackbarManager = koinInject<SnackbarManager>()
                val navigator = koinInject<Navigator>()
                CompositionLocalProvider(
                    LocalSnackbarManager provides snackbarManager,
                    LocalNavigator provides navigator
                ) {
                    App()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, OneTimeUsage::class)
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "spec:width=673dp,height=841dp")
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE, device = "id:desktop_large")
@Composable
private fun App() {
    val listNavigator = rememberListDetailPaneScaffoldNavigator<DetailItem>()
    val navController = rememberNavController()
    val navigator = LocalNavigator.current
    ObserveAsEvents(navigator.navigationAction) {
        when (it) {
            is NavigationAction.NavigateToDetail -> {
                listNavigator.navigateTo(ThreePaneScaffoldRole.Primary, it.destination)
            }

            is NavigationAction.NavigateTo -> {
                navController.navigate(it.destination) {
                    it.builder(this)
                }
            }

            is NavigationAction.NavigateUp -> {
                when (it.detail) {
                    true -> listNavigator.navigateBack()
                    false -> navController.navigateUp()
                }
            }
        }
    }


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
    val snackbarManager = LocalSnackbarManager.current
    SnackbarHandler(snackbarManager, snackbarHostState)

    val scope = rememberCoroutineScope()

    KeyGoNavigationWrapper(
        currentDestination = currentDestination,
        navigateToTopLevelDestination = {
            scope.launch {
                navigator.navigateTo(destination = it) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }

                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        onButtonClicked = {
            scope.launch {
                navigator.navigateTo(destination = RouteDestination.Home.SelectItem)
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
                    navController.navigate(RouteDestination.Home.NavGraph) {
                        popUpTo(RouteDestination.Auth) { inclusive = true }
                    }
                }
            )

            navigation<RouteDestination.TopLevelAppGraph>(
                startDestination = RouteDestination.Home.NavGraph
            ) {
                navigation<RouteDestination.Home.NavGraph>(
                    startDestination = RouteDestination.Home.Root
                ) {
                    dialog<RouteDestination.Home.SelectItem> {
                        SelectItemContent(
                            onSelect = {
                                scope.launch {
                                    navigator.navigateUp()
                                    navigator.navigateToDetail(DetailItem.Edit(it))
                                }
                            }
                        )
                    }

                    mainGraph(listNavigator = listNavigator, navigate = { /*TODO*/ })
                }

                composable<RouteDestination.Connectivity> {
                    Text("CONNECTIVITY")
                }
                composable<RouteDestination.Settings> {
                    Text("SETTINGS")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun NavGraphBuilder.mainGraph(
    listNavigator: ThreePaneScaffoldNavigator<DetailItem>,
    navigate: (RouteDestination) -> Unit
) {
    composable<RouteDestination.Home.Root> {
        NavigableListDetailPaneScaffold(
            navigator = listNavigator,
            listPane = {
                AnimatedPane {
                    DashboardScreen(navigate = navigate)
                }
            },
            detailPane = {
                AnimatedPane {
                    Surface {
                        when (val detailItem = listNavigator.currentDestination?.contentKey) {
                            is DetailItem.Edit -> {
                                val store = remember { ViewModelStore() }

                                DisposableEffect(detailItem) {
                                    onDispose {
                                        store.clear()
                                    }
                                }

                                val storeOwner = remember(store) {
                                    object : ViewModelStoreOwner {
                                        override val viewModelStore: ViewModelStore = store
                                    }
                                }

                                CompositionLocalProvider(
                                    LocalViewModelStoreOwner provides storeOwner
                                ) {
                                    PasswordScreen(navigate = navigate)
                                }
                            }

                            is DetailItem.View -> {
                                Text("View ${detailItem.item.name}")
                            }

                            else -> {}
                        }
                    }
                }
            }
        )

    }
}

