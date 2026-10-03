package de.davis.keygo.app.presentation.component

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import de.davis.keygo.app.presentation.AppDestinations
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.ui.composition.LocalNavigationBarCollapseState
import de.davis.keygo.core.ui.composition.NavigationBarCollapseState

@Composable
fun KeyGoNavigationWrapper(
    selectedRoute: NavKey?,
    navigateToTopLevelDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    onItemSelected: (VaultItemType) -> Unit,
    showChrome: Boolean = true,
    showPrimaryActionButton: Boolean = true,
    needsAttentionCount: Int = 0,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    val isBar = navigationSuiteType.isBar

    val touchExplorationEnabled = rememberTouchExplorationEnabled()
    val hidesOnScroll = isBar && !touchExplorationEnabled

    val collapseState = remember { NavigationBarCollapseState() }
    LaunchedEffect(selectedRoute, hidesOnScroll) { collapseState.reset() }

    val showNavigation = showChrome && !(hidesOnScroll && collapseState.isCollapsed)
    val scaffoldState = rememberNavigationSuiteScaffoldState(
        if (showNavigation) NavigationSuiteScaffoldValue.Visible
        else NavigationSuiteScaffoldValue.Hidden
    )
    LaunchedEffect(showNavigation) {
        if (showNavigation) scaffoldState.show() else scaffoldState.hide()
    }

    val showCreateMenu = isBar && showChrome && showPrimaryActionButton

    Surface(
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        NavigationSuiteScaffoldLayout(
            navigationSuiteType = navigationSuiteType,
            state = scaffoldState,
            navigationSuite = {
                if (isBar) ShortNavigationBar {
                    AppDestinationItems(
                        navigationSuiteType = navigationSuiteType,
                        selectedRoute = selectedRoute,
                        needsAttentionCount = needsAttentionCount,
                        onDestinationSelected = navigateToTopLevelDestination,
                    )
                }
                else KeyGoNavigationRail(
                    modal = !adaptiveInfo.windowSizeClass
                        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND),
                    visible = showChrome,
                    selectedRoute = selectedRoute,
                    needsAttentionCount = needsAttentionCount,
                    onDestinationSelected = navigateToTopLevelDestination,
                    onCreateClicked = onButtonClicked,
                )
            },
            primaryActionContent = {
                if (isBar) CreateItemMenu(
                    visible = showCreateMenu,
                    onItemSelected = onItemSelected,
                )
            },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Hidden chrome leaves the insets to the content, so full screen flows
                        // (onboarding, backup wizards) keep their buttons clear of the system bars.
                        .consumeWindowInsets(
                            if (showChrome) navigationSuiteType.insets else NoWindowInsets
                        )
                ) {
                    CompositionLocalProvider(
                        LocalNavigationBarCollapseState provides collapseState,
                        content = content,
                    )

                    // This slot ends where the bar starts, so the host already clears the bar and
                    // follows it as it collapses. Only the create button is left to pad around.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (showCreateMenu)
                                    CreateItemMenuButtonSize + PrimaryActionContentPadding
                                else 0.dp
                            )
                    ) {
                        snackbarHost()
                    }
                }
            },
        )
    }
}

private val NavigationSuiteType.isBar: Boolean
    get() = this == NavigationSuiteType.ShortNavigationBarCompact ||
            this == NavigationSuiteType.ShortNavigationBarMedium

private val NavigationSuiteType.insets: WindowInsets
    @Composable get() =
        if (isBar) ShortNavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
        else WideNavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)

private val NoWindowInsets = WindowInsets(0, 0, 0, 0)

/** The padding [NavigationSuiteScaffoldLayout] places around the primary action content. */
private val PrimaryActionContentPadding = 16.dp

/**
 * Whether an accessibility service that uses touch exploration, such as TalkBack, is running.
 *
 * Scroll driven hiding stays off while one is, the way Material does it for its own app bars: the
 * component a screen reader user navigates with must not move out from under them.
 */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager =
        remember(context) { context.getSystemService<AccessibilityManager>() }

    var enabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }

    DisposableEffect(accessibilityManager) {
        if (accessibilityManager == null) return@DisposableEffect onDispose {}

        // The service may have been switched while this was not listening.
        enabled = accessibilityManager.isTouchExplorationEnabled

        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }

    return enabled
}

@Suppress("VisualLintOverlap")
@Preview(name = "Phone")
@Preview(device = "spec:width=673dp,height=841dp", name = "Medium Tablet")
@Preview(device = "spec:width=1920dp,height=1080dp,dpi=160", name = "Desktop")
@Composable
private fun KeyGoNavigationWrapperPreview() {
    MaterialTheme {
        KeyGoNavigationWrapper(
            selectedRoute = AppDestinations.entries.first().route,
            navigateToTopLevelDestination = {},
            onButtonClicked = {},
            onItemSelected = {},
            needsAttentionCount = 3,
        ) {
            Text("Content")
        }
    }
}
