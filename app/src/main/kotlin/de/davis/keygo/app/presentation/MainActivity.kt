package de.davis.keygo.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import de.davis.keygo.R
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
import de.davis.keygo.feature.backup.presentation.BackupHubRoute
import de.davis.keygo.feature.backup.presentation.backupGraph
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.onboarding.presentation.onboardingGraph
import de.davis.keygo.feature.settings.presentation.ChangePasswordRoute
import de.davis.keygo.feature.settings.presentation.settingsGraph
import de.davis.keygo.item.dialog.SelectItemContent
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class MainActivity : FragmentActivity() {

    private val viewModel by viewModel<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            viewModel.isReturningUser.value == null
        }

        enableEdgeToEdge()
        setContent {
            val hasAccess by viewModel.isReturningUser.collectAsState()
            hasAccess ?: return@setContent

            KeyGoTheme {
                val snackbarManager = koinInject<SnackbarManager>()
                CompositionLocalProvider(
                    LocalSnackbarManager provides snackbarManager,
                ) {
                    App(hasAccess = hasAccess == true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun App(hasAccess: Boolean) {
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
            startDestination = if (hasAccess) AuthRoute() else OnboardingRoute(),
        ) {
            totpImportRedirectGraph(
                hasAccess = hasAccess,
                navigateAndReplace = { dest ->
                    navController.navigate(dest) {
                        popUpTo<TotpImportRedirect> { inclusive = true }
                    }
                }
            )

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

            onboardingGraph(
                onSuccess = { totpUri ->
                    val dest = totpUri?.let {
                        RouteDestination.Home.Root(it)
                    } ?: RouteDestination.TopLevelAppGraph

                    navController.navigate(dest) {
                        popUpTo<OnboardingRoute> { inclusive = true }
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
                    onOpenBackup = { navController.navigate(BackupHubRoute) },
                    onUp = { navController.navigateUp() },
                )

                composable<RouteDestination.Connectivity> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.coming_soon),
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                }
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

            backupGraph(
                navigateToDestination = navController::navigate,
                navigateUp = { navController.navigateUp() },
            )
        }
    }
}
