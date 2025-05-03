package de.davis.keygo.app.presentation.component

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst

@Composable
fun KeyGoNavigationSuiteScaffoldLayout(
    navigationSuite: @Composable () -> Unit,
    navigationSuiteType: NavigationSuiteType,
    state: NavigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState(),
    primaryActionContent: @Composable (() -> Unit) = {},
    primaryActionContentHorizontalAlignment: Alignment.Horizontal =
        NavigationSuiteScaffoldDefaults.primaryActionContentAlignment,
    snackbarHost: @Composable (() -> Unit) = {},
    content: @Composable () -> Unit
) {
    val animationProgress by
    animateFloatAsState(
        targetValue = if (state.currentValue == NavigationSuiteScaffoldValue.Hidden) 0f else 1f,
        animationSpec = AnimationSpec
    )

    Layout({
        // Wrap the navigation suite and content composables each in a Box to not propagate the
        // parent's (Surface) min constraints to its children (see b/312664933).
        Box(Modifier.layoutId(NavigationSuiteLayoutIdTag)) { navigationSuite() }
        Box(Modifier.layoutId(PrimaryActionContentLayoutIdTag)) { primaryActionContent() }
        Box(Modifier.layoutId(SnackbarHostIdTag)) { snackbarHost() }
        Box(Modifier.layoutId(ContentLayoutIdTag)) { content() }
    }) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Find the navigation suite composable through it's layoutId tag
        val navigationPlaceable =
            measurables
                .fastFirst { it.layoutId == NavigationSuiteLayoutIdTag }
                .measure(looseConstraints)
        val primaryActionContentPlaceable =
            measurables
                .fastFirst { it.layoutId == PrimaryActionContentLayoutIdTag }
                .measure(looseConstraints)

        val snackbarPlaceable =
            measurables
                .fastFirst { it.layoutId == SnackbarHostIdTag }
                .measure(looseConstraints)

        val isNavigationBar = navigationSuiteType.isNavigationBar
        val layoutHeight = constraints.maxHeight
        val layoutWidth = constraints.maxWidth
        // Find the content composable through it's layoutId tag.
        val contentPlaceable =
            measurables
                .fastFirst { it.layoutId == ContentLayoutIdTag }
                .measure(
                    if (isNavigationBar) {
                        constraints.copy(
                            minHeight =
                                layoutHeight -
                                        (navigationPlaceable.height * animationProgress).toInt(),
                            maxHeight =
                                layoutHeight -
                                        (navigationPlaceable.height * animationProgress).toInt()
                        )
                    } else {
                        constraints.copy(
                            minWidth =
                                layoutWidth -
                                        (navigationPlaceable.width * animationProgress).toInt(),
                            maxWidth =
                                layoutWidth -
                                        (navigationPlaceable.width * animationProgress).toInt()
                        )
                    }
                )


        val snackbarHeight = snackbarPlaceable.height

        layout(layoutWidth, layoutHeight) {
            if (isNavigationBar) {
                // Place content above the navigation component.
                contentPlaceable.placeRelative(0, 0)
                // Place the navigation component at the bottom of the screen.
                navigationPlaceable.placeRelative(
                    0,
                    layoutHeight - (navigationPlaceable.height * animationProgress).toInt()
                )

                // Place the primary action content above the navigation component.
                val positionX =
                    if (primaryActionContentHorizontalAlignment == Alignment.Start) {
                        PrimaryActionContentPadding.roundToPx()
                    } else if (
                        primaryActionContentHorizontalAlignment == Alignment.CenterHorizontally
                    ) {
                        (layoutWidth - primaryActionContentPlaceable.width) / 2
                    } else {
                        layoutWidth -
                                primaryActionContentPlaceable.width -
                                PrimaryActionContentPadding.roundToPx()
                    }

                val fabOffsetFromBottom = primaryActionContentPlaceable.height +
                        PrimaryActionContentPadding.roundToPx() +
                        (navigationPlaceable.height * animationProgress).toInt()

                primaryActionContentPlaceable.placeRelative(
                    positionX,
                    layoutHeight - fabOffsetFromBottom
                )


                val snackbarOffsetFromBottom =
                    if (snackbarHeight != 0) {
                        snackbarHeight + fabOffsetFromBottom
                    } else {
                        0
                    }
                snackbarPlaceable.placeRelative(
                    0,
                    layoutHeight - snackbarOffsetFromBottom
                )
            } else {
                // Place the navigation component at the start of the screen.
                navigationPlaceable.placeRelative(
                    (0 - (navigationPlaceable.width * (1f - animationProgress))).toInt(),
                    0
                )
                // Place content to the side of the navigation component.
                contentPlaceable.placeRelative(
                    (navigationPlaceable.width * animationProgress).toInt(),
                    0
                )

                snackbarPlaceable.placeRelative(
                    (layoutWidth - snackbarPlaceable.width) / 2,
                    layoutHeight - snackbarPlaceable.height
                )
            }
        }
    }
}

private val NavigationSuiteType.isNavigationBar
    get() =
        this == NavigationSuiteType.ShortNavigationBarCompact ||
                this == NavigationSuiteType.ShortNavigationBarMedium ||
                this == NavigationSuiteType.NavigationBar


private const val SpringDefaultSpatialDamping = 0.9f
private const val SpringDefaultSpatialStiffness = 700.0f
private const val NavigationSuiteLayoutIdTag = "navigationSuite"
private const val PrimaryActionContentLayoutIdTag = "primaryActionContent"
private const val SnackbarHostIdTag = "snackbarHost"
private const val ContentLayoutIdTag = "content"


private val PrimaryActionContentPadding = 16.dp


private val AnimationSpec: SpringSpec<Float> =
    spring(dampingRatio = SpringDefaultSpatialDamping, stiffness = SpringDefaultSpatialStiffness)