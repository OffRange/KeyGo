package de.davis.keygo.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.scene.DialogSceneStrategy
import de.davis.keygo.app.presentation.component.KeyGoNavigationWrapper
import de.davis.keygo.app.presentation.navigation.AppNavigator
import de.davis.keygo.app.presentation.navigation.keyGoEntryProvider
import de.davis.keygo.app.presentation.navigation.rememberAppNavigationState
import de.davis.keygo.app.presentation.navigation.resolveAppShell
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.core.ui.navigation.KeyGoNavDisplay
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.core.util.presentation.snackbar.SnackbarHandler
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.totp.presentation.TotpImportDeepLinkMatcher
import de.davis.keygo.feature.totp.presentation.TotpImportRedirect
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
            // Null until the account has been looked up, which the splash screen waits out.
            val hasAccess = viewModel.isReturningUser.collectAsState().value ?: return@setContent
            val isLocked by viewModel.isLocked.collectAsState()
            val isSessionActive by viewModel.isSessionActive.collectAsState()

            KeyGoTheme {
                val snackbarManager = koinInject<SnackbarManager>()
                CompositionLocalProvider(
                    LocalSnackbarManager provides snackbarManager,
                ) {
                    App(
                        hasAccess = hasAccess,
                        launchRoute = launchRoute(hasAccess),
                        isLocked = isLocked,
                        isSessionActive = isSessionActive,
                    )
                }
            }
        }
    }

    private fun launchRoute(hasAccess: Boolean): NavKey =
        intent.totpImportRedirect() ?: if (hasAccess) AuthRoute() else OnboardingRoute()
}

private fun Intent.totpImportRedirect(): TotpImportRedirect? {
    // A DeepLinkRequest with neither a uri nor extras throws, and the launcher intent has no data.
    val uri = data ?: return null
    return TotpImportDeepLinkMatcher.match(DeepLinkRequest(uri))?.key
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun App(
    hasAccess: Boolean,
    launchRoute: NavKey,
    isLocked: Boolean,
    isSessionActive: Boolean,
) {
    val navigationState = rememberAppNavigationState(
        launchRoute = launchRoute,
        startRoute = RouteDestination.Home,
        topLevelRoutes = TopLevelRoutes,
    )
    val navigator = remember(navigationState) { AppNavigator(navigationState) }

    LockAppWhenSessionEnds(isLocked, isSessionActive, navigator)

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
    }
    val listPaneVisible = directive.maxHorizontalPartitions > 1

    DropAutoSelectedDetailWhenListLeaves(listPaneVisible, navigator)

    val entries = navigationState.toDecoratedEntries(keyGoEntryProvider(navigator, hasAccess))
    val shell = entries.resolveAppShell(listPaneVisible)

    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val sceneStrategies = remember(listDetailStrategy) {
        listOf(DialogSceneStrategy(), listDetailStrategy)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarHandler(snackbarHostState)

    CompositionLocalProvider(LocalIsInSinglePaneMode provides !listPaneVisible) {
        KeyGoNavigationWrapper(
            selectedRoute = navigationState.topLevelRoute,
            navigateToTopLevelDestination = { navigator.navigate(it) },
            onButtonClicked = { navigator.navigate(RouteDestination.SelectItemType) },
            onItemSelected = { type -> navigator.showDetail(RouteDestination.CreateItem(type)) },
            showChrome = shell.showNavigation,
            showPrimaryActionButton = shell.showCreateButton,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) {
            KeyGoNavDisplay(
                entries = entries,
                onBack = { navigator.goBack() },
                sceneStrategies = sceneStrategies,
            )
        }
    }
}

/**
 * Locks the app in two cases:
 * - [isLocked] catches the instant a session that was active ends, whether the app proper
 *   currently owns the window or a launch-flow screen does (an in-progress TOTP-import picker,
 *   say) - [AppNavigator.lock] pushes over either without disturbing what's underneath.
 * - The level check (`!isSessionActive && !isLaunching`) catches a back stack a configuration
 *   change or process death restored straight into the app proper with a session that never got
 *   re-established: [isLocked] alone can't see this, since it only fires on a transition a freshly
 *   restored [AppViewModel] has no memory of. Gated by `!isLaunching` so it never fires during
 *   onboarding or the very first login (both show with the launch flow already owning the window
 *   and no session yet, which looks the same as this case unless launch state is checked too), and
 *   so it never fights [AppNavigator.lock]'s own idempotency for a gate or picker Nav3 already
 *   restored correctly.
 */
@Composable
private fun LockAppWhenSessionEnds(
    isLocked: Boolean,
    isSessionActive: Boolean,
    navigator: AppNavigator,
) {
    val isLaunching = navigator.state.isLaunching
    LaunchedEffect(isLocked, isSessionActive, isLaunching) {
        if (isLocked || (!isSessionActive && !isLaunching)) navigator.lock(AuthRoute())
    }
}

/**
 * Auto-selection is fine beside the list and wrong once the window narrows enough to hand the
 * detail the whole screen. Only a change is acted on, so a detail restored after process death
 * stays put.
 *
 * The previous width is saved rather than merely remembered: rotating or folding the device is
 * both what this watches for and what recreates the Activity, and a plain `remember` would come
 * back seeded with the width it was supposed to compare against, seeing no change at all.
 */
@Composable
private fun DropAutoSelectedDetailWhenListLeaves(
    listPaneVisible: Boolean,
    navigator: AppNavigator,
) {
    var wasListPaneVisible by rememberSaveable { mutableStateOf(listPaneVisible) }
    LaunchedEffect(listPaneVisible) {
        val listPaneLeft = wasListPaneVisible && !listPaneVisible
        wasListPaneVisible = listPaneVisible
        if (listPaneLeft) navigator.dropAutoSelectedDetail()
    }
}
