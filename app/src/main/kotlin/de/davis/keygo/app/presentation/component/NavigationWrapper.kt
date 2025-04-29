package de.davis.keygo.app.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.window.core.layout.WindowSizeClass
import de.davis.keygo.R
import de.davis.keygo.app.presentation.AppDestinations
import de.davis.keygo.generated.Route
import kotlinx.coroutines.launch


@Composable
fun KeyGoNavigationWrapper(
    currentDestination: NavDestination?,
    navigateToTopLevelDestination: (Route) -> Unit,
    onButtonClicked: () -> Unit,
    showChrome: Boolean = true,
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    buttonContainerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    buttonContentColor: Color = contentColorFor(buttonContainerColor),
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSize = with(LocalDensity.current) {
        currentWindowSize().toSize().toDpSize()
    }

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

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState
            ) {
                DrawerContent(
                    currentDestination = currentDestination,
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
                    Box {
                        AnimatedVisibility(
                            visible = showChrome,
                            enter = when (layoutType) {
                                NavigationSuiteType.NavigationBar -> expandVertically()
                                else -> expandHorizontally()
                            } + fadeIn(),
                            exit = when (layoutType) {
                                NavigationSuiteType.NavigationBar -> shrinkVertically()
                                else -> shrinkHorizontally()
                            } + fadeOut()
                        ) {
                            KeyGoNavigationSuite(
                                currentDestination = currentDestination,
                                layoutType = layoutType,
                                navigateToTopLvlDestination = navigateToTopLevelDestination,
                                onButtonClicked = onButtonClicked,
                                onOpenDrawer = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                },
                                buttonContainerColor = buttonContainerColor,
                                buttonContentColor = buttonContentColor
                            )
                        }
                    }
                },
                layoutType = layoutType,
                content = {
                    Box(
                        Modifier.consumeWindowInsets(
                            when (layoutType) {
                                NavigationSuiteType.NavigationBar ->
                                    NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)

                                NavigationSuiteType.NavigationRail ->
                                    NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)

                                NavigationSuiteType.NavigationDrawer ->
                                    DrawerDefaults.windowInsets.only(WindowInsetsSides.Start)

                                else -> WindowInsets(0, 0, 0, 0)
                            }
                        )
                    ) {
                        content()
                    }
                }
            )
        }
    }
}

@Composable
fun KeyGoNavigationSuite(
    currentDestination: NavDestination?,
    layoutType: NavigationSuiteType,
    navigateToTopLvlDestination: (Route) -> Unit,
    onButtonClicked: () -> Unit,
    onOpenDrawer: () -> Unit,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor)
) {
    when (layoutType) {
        NavigationSuiteType.NavigationBar -> {
            KeyGoNavigationBar(
                currentDestination = currentDestination,
                navigateToTopLvlDestination = navigateToTopLvlDestination
            )
        }

        NavigationSuiteType.NavigationRail -> {
            KeyGoNavigationRail(
                currentDestination = currentDestination,
                navigateToTopLvlDestination = navigateToTopLvlDestination,
                onButtonClicked = onButtonClicked,
                onOpenDrawer = onOpenDrawer,
                buttonContainerColor = buttonContainerColor,
                buttonContentColor = buttonContentColor
            )
        }

        NavigationSuiteType.NavigationDrawer -> {
            KeyGoNavigationDrawer(
                currentDestination = currentDestination,
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
    currentDestination: NavDestination?,
    navigateToTopLvlDestination: (Route) -> Unit
) {
    NavigationBar {
        AppDestinations.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination.hasRoute(destination.route),
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
    currentDestination: NavDestination?,
    navigateToTopLvlDestination: (Route) -> Unit,
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
                    contentDescription = stringResource(R.string.add_content_description),
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
                    selected = currentDestination.hasRoute(destination.route),
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
    currentDestination: NavDestination?,
    navigateToTopLvlDestination: (Route) -> Unit,
    onButtonClicked: () -> Unit,
    buttonContainerColor: Color = FloatingActionButtonDefaults.containerColor,
    buttonContentColor: Color = contentColorFor(buttonContainerColor)
) {
    PermanentDrawerSheet(
        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp)
    ) {
        DrawerContent(
            currentDestination = currentDestination,
            navigateToTopLvlDestination = navigateToTopLvlDestination,
            onButtonClicked = onButtonClicked,
            buttonContainerColor = buttonContainerColor,
            buttonContentColor = buttonContentColor
        )
    }
}

@Composable
fun DrawerContent(
    currentDestination: NavDestination?,
    navigateToTopLvlDestination: (Route) -> Unit,
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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            onCloseDrawer?.let {
                IconButton(
                    onClick = it
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.MenuOpen,
                        contentDescription = stringResource(R.string.close_navigation_drawer_content_description)
                    )
                }
            }
        }

        // TODO decide if this or the default ExtendedFab is better (ExtendedFabTextPadding)
        FloatingActionButton(
            onClick = onButtonClicked,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor,
        ) {
            Row(
                modifier =
                    Modifier
                        .sizeIn(minWidth = 80.dp /*ExtendedFabMinimumWidth*/)
                        .padding(horizontal = 16.dp /*ExtendedFabTextPadding - 4.dp*/),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_content_description),
                )
                Text(
                    text = stringResource(R.string.add),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

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
                    selected = currentDestination.hasRoute(destination.route),
                    onClick = { navigateToTopLvlDestination(destination.route) },
                )
            }
        }
    }
}

fun NavDestination?.hasRoute(dest: Route): Boolean {
    return this?.hasRoute(dest::class) == true
}

@Suppress("VisualLintOverlap")
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Default")
@Preview(device = "spec:width=673dp,height=841dp", name = "Medium Tablet")
@Preview(device = "spec:width=1920dp,height=1080dp,dpi=160", name = "Desktop")
@Composable
private fun KeyGoNavigationWrapperPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            KeyGoNavigationWrapper(
                currentDestination = null,
                navigateToTopLevelDestination = {},
                onButtonClicked = {},
            ) {
                Text("ASASASASAS")
            }
        }
    }
}