package de.davis.keygo.app.presentation.component

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import de.davis.keygo.R
import de.davis.keygo.app.presentation.AppDestinations
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import kotlinx.coroutines.launch
import kotlin.math.sign
import de.davis.keygo.core.ui.R as CoreUiR


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyGoNavigationWrapper(
    selectedRoute: NavKey?,
    navigateToTopLevelDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    onItemSelected: (VaultItemType) -> Unit,
    showChrome: Boolean = true,
    showPrimaryActionButton: Boolean = true,
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    buttonContainerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    buttonContentColor: Color = contentColorFor(buttonContainerColor),
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSize = LocalWindowInfo.current.containerDpSize

    val layoutType = when {
        adaptiveInfo.windowPosture.isTabletop -> NavigationSuiteType.NavigationBar

        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
                windowSize.width >= 1200.dp -> NavigationSuiteType.NavigationDrawer

        adaptiveInfo.windowSizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
        ) -> NavigationSuiteType.NavigationRail

        else -> NavigationSuiteType.NavigationBar
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val scaffoldState = rememberNavigationSuiteScaffoldState()

    val touchExplorationEnabled = rememberTouchExplorationEnabled()
    val hidesOnScroll =
        layoutType == NavigationSuiteType.NavigationBar && !touchExplorationEnabled

    var hiddenByScroll by remember { mutableStateOf(false) }

    // A newly selected top level destination shows its own content from the top, and a layout
    // type that does not hide leaves nothing to come back from, so both start the component
    // visible again.
    LaunchedEffect(selectedRoute, hidesOnScroll) { hiddenByScroll = false }

    val showNavigation = showChrome && !(hidesOnScroll && hiddenByScroll)
    LaunchedEffect(showNavigation) {
        if (showNavigation) scaffoldState.show() else scaffoldState.hide()
    }

    val density = LocalDensity.current
    val scrollConnection = remember(density) {
        NavigationScrollConnection(
            thresholdPx = with(density) { NavigationScrollThreshold.toPx() },
            onVisibilityChange = { visible -> hiddenByScroll = !visible },
        )
    }

    // Height of the primary action button, so the snackbar can clear it. Measured on the
    // button itself and not on its menu, which grows to the full item list when expanded.
    var primaryActionHeight by remember { mutableIntStateOf(0) }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState
            ) {
                DrawerContent(
                    selectedRoute = selectedRoute,
                    navigateToTopLvlDestination = navigateToTopLevelDestination,
                    onButtonClicked = onButtonClicked,
                    onCloseDrawer = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    buttonContainerColor = buttonContainerColor,
                    buttonContentColor = buttonContentColor
                )
            }
        },
        gesturesEnabled = false,
        drawerState = drawerState
    ) {
        Surface(color = containerColor, contentColor = contentColor) {
            NavigationSuiteScaffoldLayout(
                navigationSuite = {
                    KeyGoNavigationSuite(
                        selectedRoute = selectedRoute,
                        layoutType = layoutType,
                        navigateToTopLvlDestination = navigateToTopLevelDestination,
                        onButtonClicked = onButtonClicked,
                        onOpenDrawer = {
                            scope.launch {
                                drawerState.open()
                            }
                        },
                        buttonContainerColor = buttonContainerColor,
                        buttonContentColor = buttonContentColor,
                    )
                },
                navigationSuiteType = layoutType,
                state = scaffoldState,
                primaryActionContent = {
                    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
                    val focusRequester = remember { FocusRequester() }

                    val showPrimaryAction = showChrome && showPrimaryActionButton

                    // The open menu draws no scrim and consumes nothing outside its items, so the
                    // destination underneath keeps taking taps and can navigate away while the
                    // menu is still open. The menu belongs to the shell and outlives that
                    // navigation, so a destination that drops the button takes the menu with it.
                    LaunchedEffect(showPrimaryAction) {
                        if (!showPrimaryAction) fabMenuExpanded = false
                    }

                    FloatingActionButtonMenu(
                        expanded = fabMenuExpanded,
                        modifier = Modifier.animateFloatingActionButton(
                            visible = showPrimaryAction || fabMenuExpanded,
                            alignment = Alignment.BottomEnd,
                        ),
                        button = {
                            TooltipBox(
                                positionProvider =
                                    TooltipDefaults.rememberTooltipPositionProvider(
                                        if (fabMenuExpanded) {
                                            TooltipAnchorPosition.Start
                                        } else {
                                            TooltipAnchorPosition.Above
                                        }
                                    ),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.add_element_content_description)) } },
                                state = rememberTooltipState(),
                            ) {
                                ToggleFloatingActionButton(
                                    checked = fabMenuExpanded,
                                    onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                                    modifier = Modifier
                                        .onSizeChanged { primaryActionHeight = it.height }
                                        .semantics {
                                            traversalIndex = -1f
                                        }
                                        .focusRequester(focusRequester),
                                ) {
                                    val imageVector by remember {
                                        derivedStateOf {
                                            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                                        }
                                    }
                                    Icon(
                                        painter = rememberVectorPainter(imageVector),
                                        contentDescription = null,
                                        modifier = Modifier.animateIcon({ checkedProgress }),
                                    )
                                }
                            }
                        }
                    ) {
                        VaultItemType.entries.forEach { type ->
                            val (text, icon) = type.presentation
                            FloatingActionButtonMenuItem(
                                onClick = {
                                    fabMenuExpanded = false
                                    onItemSelected(type)
                                },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null
                                    )
                                },
                                text = { Text(text = text) }
                            )
                        }
                    }
                },
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(navigationInsets(layoutType, scaffoldState))
                            .then(
                                if (hidesOnScroll) Modifier.nestedScroll(scrollConnection)
                                else Modifier
                            )
                    ) {
                        content()

                        // This slot ends where the navigation component starts, so a bottom
                        // aligned host clears the component on its own and follows it as it
                        // collapses. Only the primary action button is left to pad around.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = if (showChrome && showPrimaryActionButton)
                                        with(LocalDensity.current) { primaryActionHeight.toDp() } +
                                                PrimaryActionContentPadding
                                    else 0.dp
                                )
                        ) {
                            snackbarHost()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun KeyGoNavigationSuite(
    selectedRoute: NavKey?,
    layoutType: NavigationSuiteType,
    navigateToTopLvlDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    onOpenDrawer: () -> Unit,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor),
) {
    when (layoutType) {
        NavigationSuiteType.NavigationBar -> {
            KeyGoNavigationBar(
                selectedRoute = selectedRoute,
                navigateToTopLvlDestination = navigateToTopLvlDestination,
            )
        }

        NavigationSuiteType.NavigationRail -> {
            KeyGoNavigationRail(
                selectedRoute = selectedRoute,
                navigateToTopLvlDestination = navigateToTopLvlDestination,
                onButtonClicked = onButtonClicked,
                onOpenDrawer = onOpenDrawer,
                buttonContainerColor = buttonContainerColor,
                buttonContentColor = buttonContentColor
            )
        }

        NavigationSuiteType.NavigationDrawer -> {
            KeyGoNavigationDrawer(
                selectedRoute = selectedRoute,
                onButtonClicked = onButtonClicked,
                navigateToTopLvlDestination = navigateToTopLvlDestination,
                buttonContainerColor = buttonContainerColor,
                buttonContentColor = buttonContentColor
            )
        }

        else -> {}
    }
}

@Composable
fun KeyGoNavigationBar(
    selectedRoute: NavKey?,
    navigateToTopLvlDestination: (NavKey) -> Unit,
) {
    NavigationBar {
        AppDestinations.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination.route == selectedRoute,
                onClick = { navigateToTopLvlDestination(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.contentDescription)
                    )
                },
                label = { Text(text = stringResource(destination.label)) },
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
fun KeyGoNavigationRail(
    selectedRoute: NavKey?,
    navigateToTopLvlDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    onOpenDrawer: () -> Unit,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor)
) {
    NavigationRail(
        header = {
            NavigationRailItem(
                selected = false,
                onClick = onOpenDrawer,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.open_navigation_drawer_content_description)
                    )
                }
            )

            FloatingActionButton(
                onClick = onButtonClicked,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                containerColor = buttonContainerColor,
                contentColor = buttonContentColor,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_element_content_description),
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppDestinations.entries.forEach { destination ->
                NavigationRailItem(
                    selected = destination.route == selectedRoute,
                    onClick = { navigateToTopLvlDestination(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.contentDescription)
                        )
                    },
                    label = { Text(text = stringResource(destination.label)) },
                    alwaysShowLabel = false
                )
            }
        }
    }
}

@Composable
fun KeyGoNavigationDrawer(
    selectedRoute: NavKey?,
    navigateToTopLvlDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor)
) {
    PermanentDrawerSheet(
        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp)
    ) {
        DrawerContent(
            selectedRoute = selectedRoute,
            navigateToTopLvlDestination = navigateToTopLvlDestination,
            onButtonClicked = onButtonClicked,
            buttonContainerColor = buttonContainerColor,
            buttonContentColor = buttonContentColor
        )
    }
}

@Composable
fun DrawerContent(
    selectedRoute: NavKey?,
    navigateToTopLvlDestination: (NavKey) -> Unit,
    onButtonClicked: () -> Unit,
    onCloseDrawer: (() -> Unit)? = null,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor)
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CoreUiR.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            onCloseDrawer?.let {
                IconButton(
                    onClick = it
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.MenuOpen,
                        contentDescription = stringResource(R.string.close_content_description)
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onButtonClicked,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor,
            text = {
                Text(
                    text = stringResource(CoreUiR.string.add),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_element_content_description),
                )
            }
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppDestinations.entries.forEach { destination ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.contentDescription)
                        )
                    },
                    label = {
                        Text(text = stringResource(destination.label))
                    },
                    selected = destination.route == selectedRoute,
                    onClick = { navigateToTopLvlDestination(destination.route) },
                )
            }
        }
    }
}

@Composable
private fun navigationInsets(
    layoutType: NavigationSuiteType,
    state: NavigationSuiteScaffoldState,
): WindowInsets =
    if (state.currentValue == NavigationSuiteScaffoldValue.Hidden && !state.isAnimating)
        WindowInsets(0, 0, 0, 0)
    else when (layoutType) {
        NavigationSuiteType.NavigationBar ->
            NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)

        NavigationSuiteType.NavigationRail ->
            NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)

        NavigationSuiteType.NavigationDrawer ->
            DrawerDefaults.windowInsets.only(WindowInsetsSides.Start)

        else -> WindowInsets(0, 0, 0, 0)
    }

/**
 * Hides the navigation component once the content has been scrolled [thresholdPx] down, and brings
 * it back on the same distance scrolled up.
 *
 * Only the distance the content actually consumed counts, so overscrolling at either end of a list
 * does not move the component, and content that cannot scroll at all never hides it.
 */
private class NavigationScrollConnection(
    private val thresholdPx: Float,
    private val onVisibilityChange: (visible: Boolean) -> Unit,
) : NestedScrollConnection {

    private var accumulated = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // A scroll that moved the content nowhere, a horizontal one included, leaves the run
        // it interrupted intact.
        val delta = consumed.y
        if (delta != 0f) {
            // A change of direction starts a new run, so scrolling back reverses the decision
            // after one threshold instead of first having to undo the whole distance travelled.
            if (delta.sign != accumulated.sign) accumulated = 0f
            accumulated = (accumulated + delta).coerceIn(-thresholdPx, thresholdPx)

            if (accumulated <= -thresholdPx) onVisibilityChange(false)
            else if (accumulated >= thresholdPx) onVisibilityChange(true)
        }

        // Nothing is consumed here: the scroll belongs to the content, this only watches it.
        return super.onPostScroll(consumed, available, source)
    }
}

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
        remember(context) { context.getSystemService(AccessibilityManager::class.java) }

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

/** The padding [NavigationSuiteScaffoldLayout] places around the primary action content. */
private val PrimaryActionContentPadding = 16.dp

/** How far the content has to be scrolled before the navigation component follows it away. */
private val NavigationScrollThreshold = 24.dp

@Suppress("VisualLintOverlap")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Default")
@Preview(device = "spec:width=673dp,height=841dp", name = "Medium Tablet")
@Preview(device = "spec:width=1920dp,height=1080dp,dpi=160", name = "Desktop")
@Composable
private fun KeyGoNavigationWrapperPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            KeyGoNavigationWrapper(
                selectedRoute = null,
                navigateToTopLevelDestination = {},
                onButtonClicked = {},
                onItemSelected = {},
            ) {
                Text("ASASASASAS")
            }
        }
    }
}