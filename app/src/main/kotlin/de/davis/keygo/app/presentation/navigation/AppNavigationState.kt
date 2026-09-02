package de.davis.keygo.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.navigation.rememberNavEntryDecorators

/**
 * Creates the app's navigation state. It survives configuration changes and process death.
 *
 * @param launchRoute what the overlay starts on. Only used the first time the state is created;
 *   after that the saved stack wins.
 * @param startRoute the top level route the app opens on. Must be one of [topLevelRoutes].
 * @param topLevelRoutes the navigation bar's destinations, one back stack each.
 */
@Composable
fun rememberAppNavigationState(
    launchRoute: NavKey,
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
): AppNavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) {
        mutableStateOf(startRoute)
    }
    val overlayStack = rememberNavBackStack(launchRoute)
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        AppNavigationState(
            overlayStack = overlayStack,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks,
        )
    }
}

/**
 * The app's navigation state, modified through [AppNavigator]. It holds two things:
 *
 * - The **overlay stack**: the unlock gate, first run, or an incoming `otpauth://` link. While it
 *   holds anything it is all that shows.
 * - One **back stack per top level route**, each keeping its own history. Only the selected one is
 *   shown, with nothing underneath it, so back out of its base leaves the app.
 */
class AppNavigationState(
    val overlayStack: NavBackStack<NavKey>,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {

    /** The selected navigation bar destination. */
    var topLevelRoute: NavKey by topLevelRoute

    /** Whether the overlay owns the window, hiding the app proper underneath. */
    val isOverlaid: Boolean get() = overlayStack.isNotEmpty()

    /** The stack destinations are currently pushed onto and popped from. */
    val currentStack: NavBackStack<NavKey>
        get() = if (isOverlaid) overlayStack else backStacks.getValue(topLevelRoute)

    /**
     * What the detail pane is showing, or null while the list has the window to itself.
     *
     * A dialog is pushed onto the same stack but is drawn over the pane rather than taking it, so
     * it is looked past. Reporting nothing while one is open makes the list pick a row on its own
     * and push it above the dialog, which closes the dialog and leaves the pane the only thing the
     * scene knows about.
     */
    val openDetail: RouteDestination.Detail?
        get() = currentStack.filterIsInstance<RouteDestination.Detail>().lastOrNull()

    /**
     * Turns the state into the entries the display renders. Every stack keeps its own decorators,
     * so a route that is off screen still holds its saved state and view models.
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        val overlayEntries = rememberDecoratedEntries(overlayStack, entryProvider)
        val topLevelEntries = backStacks.mapValues { (_, stack) ->
            rememberDecoratedEntries(stack, entryProvider)
        }

        return if (isOverlaid) overlayEntries
        else topLevelEntries.getValue(topLevelRoute)
    }
}

@Composable
private fun rememberDecoratedEntries(
    backStack: NavBackStack<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators = rememberNavEntryDecorators(),
    entryProvider = entryProvider,
)
